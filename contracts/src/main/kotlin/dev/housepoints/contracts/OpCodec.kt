package dev.housepoints.contracts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/** Result of reading an op from bytes that came from storage, a peer or an export file. */
public sealed interface DecodeResult {
    public data class Decoded(val op: Op) : DecodeResult
    public data class Malformed(val reason: String) : DecodeResult
}

/** A known payload type whose body could not be read. Kept and forwarded like an unknown one. */
public data class MalformedPayload(val type: String, val json: String, val reason: String) : Payload

/**
 * Converts between [Op]s and bytes (SAD §4.1), and between payload bodies and [Payload]s.
 *
 * Envelope bytes: `0x01 ‖ opId(16) ‖ familyId(16) ‖ originDevice(16) ‖ originSeq(i64) ‖ lamport(i64) ‖
 * schemaVersion(i32) ‖ typeLen(u32) ‖ type(utf8) ‖ bodyLen(u32) ‖ body(utf8)`, all big-endian.
 */
public object OpCodec {
    private const val FORMAT_V1: Byte = 0x01
    private const val FIXED_BYTES: Int = 1 + 3 * Uuids.BYTES + Long.SIZE_BYTES * 2 + Int.SIZE_BYTES * 3

    /** Upper bound for a single op; anything bigger is hostile or corrupt. */
    public const val MAX_OP_BYTES: Int = 64 * 1024

    public val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val serializers: Map<String, KSerializer<out Payload>> = mapOf(
        PayloadType.FAMILY_CREATED to FamilyCreated.serializer(),
        PayloadType.DEVICE_UPSERT to DeviceUpsert.serializer(),
        PayloadType.DEVICE_REMOVED to DeviceRemoved.serializer(),
        PayloadType.CHILD_UPSERT to ChildUpsert.serializer(),
        PayloadType.CHORE_UPSERT to ChoreUpsert.serializer(),
        PayloadType.VALUE_UPSERT to ValueUpsert.serializer(),
        PayloadType.GOAL_UPSERT to GoalUpsert.serializer(),
        PayloadType.ENTRY_RECORDED to EntryRecorded.serializer(),
        PayloadType.POLICY_SET to PolicySet.serializer(),
        PayloadType.TICK_SET to TickSet.serializer(),
    )

    /** The `(type, body)` pair to put in a new op. */
    public fun encodePayload(payload: Payload): Pair<String, String> = when (payload) {
        is FamilyCreated -> PayloadType.FAMILY_CREATED to json.encodeToString(FamilyCreated.serializer(), payload)
        is DeviceUpsert -> PayloadType.DEVICE_UPSERT to json.encodeToString(DeviceUpsert.serializer(), payload)
        is DeviceRemoved -> PayloadType.DEVICE_REMOVED to json.encodeToString(DeviceRemoved.serializer(), payload)
        is ChildUpsert -> PayloadType.CHILD_UPSERT to json.encodeToString(ChildUpsert.serializer(), payload)
        is ChoreUpsert -> PayloadType.CHORE_UPSERT to json.encodeToString(ChoreUpsert.serializer(), payload)
        is ValueUpsert -> PayloadType.VALUE_UPSERT to json.encodeToString(ValueUpsert.serializer(), payload)
        is GoalUpsert -> PayloadType.GOAL_UPSERT to json.encodeToString(GoalUpsert.serializer(), payload)
        is EntryRecorded -> PayloadType.ENTRY_RECORDED to json.encodeToString(EntryRecorded.serializer(), payload)
        is PolicySet -> PayloadType.POLICY_SET to json.encodeToString(PolicySet.serializer(), payload)
        is TickSet -> PayloadType.TICK_SET to json.encodeToString(TickSet.serializer(), payload)
        is UnknownPayload -> payload.type to payload.json
        is MalformedPayload -> payload.type to payload.json
    }

    /** Never throws: unknown types and unreadable bodies come back as values. */
    public fun decodePayload(op: Op): Payload {
        val serializer = serializers[op.type]
        if (serializer == null || op.schemaVersion > Op.CURRENT_SCHEMA) return UnknownPayload(op.type, op.body)
        return try {
            json.decodeFromString(serializer, op.body)
        } catch (e: SerializationException) {
            MalformedPayload(op.type, op.body, e.message ?: "unreadable")
        } catch (e: IllegalArgumentException) {
            MalformedPayload(op.type, op.body, e.message ?: "invalid value")
        } catch (e: java.time.DateTimeException) {
            MalformedPayload(op.type, op.body, e.message ?: "invalid date or zone")
        }
    }

    public fun encode(op: Op): ByteArray {
        val type = op.type.toByteArray(Charsets.UTF_8)
        val body = op.body.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(FIXED_BYTES + type.size + body.size)
        buffer.put(FORMAT_V1)
        Uuids.write(buffer, op.opId.uuid)
        Uuids.write(buffer, op.familyId.uuid)
        Uuids.write(buffer, op.originDevice.uuid)
        buffer.putLong(op.originSeq.value)
        buffer.putLong(op.lamport.value)
        buffer.putInt(op.schemaVersion)
        buffer.putInt(type.size).put(type)
        buffer.putInt(body.size).put(body)
        return buffer.array()
    }

    public fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > MAX_OP_BYTES) return DecodeResult.Malformed("op larger than $MAX_OP_BYTES bytes")
        return try {
            readEnvelope(ByteBuffer.wrap(bytes))
        } catch (e: BufferUnderflowException) {
            DecodeResult.Malformed("truncated op")
        }
    }

    private fun readEnvelope(buffer: ByteBuffer): DecodeResult {
        val format = buffer.get()
        if (format != FORMAT_V1) return DecodeResult.Malformed("unsupported envelope format $format")
        val opId = OpId(Uuids.read(buffer))
        val familyId = FamilyId(Uuids.read(buffer))
        val device = DeviceId(Uuids.read(buffer))
        val seq = Seq(buffer.long)
        val lamport = Lamport(buffer.long)
        val schema = buffer.int
        val type = readString(buffer) ?: return DecodeResult.Malformed("bad type length")
        val body = readString(buffer) ?: return DecodeResult.Malformed("bad body length")
        return when {
            buffer.hasRemaining() -> DecodeResult.Malformed("trailing bytes")
            seq.value < 1 -> DecodeResult.Malformed("sequence must start at 1")
            lamport.value < 1 -> DecodeResult.Malformed("lamport must be positive")
            else -> DecodeResult.Decoded(Op(opId, familyId, device, seq, lamport, schema, type, body))
        }
    }

    private fun readString(buffer: ByteBuffer): String? {
        val length = buffer.int
        if (length < 0 || length > buffer.remaining()) return null
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
