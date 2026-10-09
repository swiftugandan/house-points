package dev.housepoints.sync

import dev.housepoints.contracts.DecodeResult
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import java.io.ByteArrayOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Byte layouts of the sync protocol messages (SAD §4.3). Every frame is `type u8 ‖ length u32 ‖ body`.
 * Readers return null for anything malformed and never throw.
 */
internal object Wire {
    const val HELLO: Byte = 1
    const val AUTH: Byte = 2
    const val VECTOR: Byte = 3
    const val OPS: Byte = 4
    const val DONE: Byte = 5

    const val HEADER_BYTES = 5
    const val NONCE_BYTES = 32
    const val MAX_FRAME_BYTES = 512 * 1024
    const val MAX_OPS_PER_BATCH = 256
    const val MAX_BATCH_BYTES = 256 * 1024

    class Hello(val protocol: Int, val family: FamilyId, val device: DeviceId, val nonce: ByteArray)

    fun header(type: Byte, bodyLength: Int): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).put(type).putInt(bodyLength).array()

    fun frame(type: Byte, body: ByteArray): ByteArray = header(type, body.size) + body

    fun parse(frame: ByteArray): Pair<Byte, ByteArray>? {
        if (frame.size < HEADER_BYTES || frame.size > MAX_FRAME_BYTES) return null
        val buffer = ByteBuffer.wrap(frame)
        val type = buffer.get()
        val length = buffer.int
        if (length != frame.size - HEADER_BYTES) return null
        return type to frame.copyOfRange(HEADER_BYTES, frame.size)
    }

    fun hello(hello: Hello): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 2 * Uuids.BYTES + NONCE_BYTES)
        buffer.put(hello.protocol.toByte())
        Uuids.write(buffer, hello.family.uuid)
        Uuids.write(buffer, hello.device.uuid)
        buffer.put(hello.nonce)
        return buffer.array()
    }

    fun readHello(body: ByteArray): Hello? = read(body) { buffer ->
        val protocol = buffer.get().toInt()
        val family = FamilyId(Uuids.read(buffer))
        val device = DeviceId(Uuids.read(buffer))
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        Hello(protocol, family, device, nonce)
    }

    fun vector(vector: VersionVector): ByteArray {
        val entries = vector.entries.entries.sortedWith { x, y -> x.key.compareTo(y.key) }
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + entries.size * (Uuids.BYTES + Long.SIZE_BYTES))
        buffer.putInt(entries.size)
        entries.forEach { (device, seq) ->
            Uuids.write(buffer, device.uuid)
            buffer.putLong(seq.value)
        }
        return buffer.array()
    }

    fun readVector(body: ByteArray): VersionVector? = read(body) { buffer ->
        val count = buffer.int
        if (count < 0 || count.toLong() * (Uuids.BYTES + Long.SIZE_BYTES) > buffer.remaining()) return@read null
        VersionVector((1..count).associate { DeviceId(Uuids.read(buffer)) to Seq(buffer.long) })
    }

    /** Splits [ops] into OPS bodies within the batch limits. */
    fun opsBatches(ops: List<Op>): List<ByteArray> {
        val batches = ArrayList<ByteArray>()
        var current = ArrayList<ByteArray>()
        var currentBytes = 0
        fun flush() {
            if (current.isEmpty()) return
            val out = ByteArrayOutputStream(currentBytes + Int.SIZE_BYTES * (current.size + 1))
            out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(current.size).array())
            current.forEach { encoded ->
                out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encoded.size).array())
                out.write(encoded)
            }
            batches += out.toByteArray()
            current = ArrayList()
            currentBytes = 0
        }
        ops.forEach { op ->
            val encoded = OpCodec.encode(op)
            val wouldOverflow = currentBytes + encoded.size + Int.SIZE_BYTES > MAX_BATCH_BYTES
            if (current.size == MAX_OPS_PER_BATCH || wouldOverflow) flush()
            current += encoded
            currentBytes += encoded.size + Int.SIZE_BYTES
        }
        flush()
        return batches
    }

    fun readOps(body: ByteArray): List<Op>? = read(body) { buffer ->
        val count = buffer.int
        if (count < 0 || count > MAX_OPS_PER_BATCH) return@read null
        val ops = ArrayList<Op>(count)
        repeat(count) {
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return@read null
            val encoded = ByteArray(length).also { buffer.get(it) }
            when (val decoded = OpCodec.decode(encoded)) {
                is DecodeResult.Decoded -> ops += decoded.op
                is DecodeResult.Malformed -> return@read null
            }
        }
        ops
    }

    fun done(sent: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(sent).array()

    fun readDone(body: ByteArray): Int? = read(body) { buffer -> buffer.int }

    /** Runs [reader] and requires it to consume the body exactly. */
    private inline fun <T> read(body: ByteArray, reader: (ByteBuffer) -> T?): T? {
        val buffer = ByteBuffer.wrap(body)
        return try {
            reader(buffer)?.takeIf { !buffer.hasRemaining() }
        } catch (e: BufferUnderflowException) {
            null
        }
    }
}
