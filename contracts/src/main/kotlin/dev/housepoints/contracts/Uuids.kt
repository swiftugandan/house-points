package dev.housepoints.contracts

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * UUID versions the JDK does not provide (RFC 9562 [SPEC R6]): v5 (name-based, SHA-1) for deterministic
 * entry ids and v7 (Unix-time ordered) for everything else.
 *
 * v7 layout: unix_ts_ms(48) ver(4) rand_a(12) var(2) rand_b(62).
 */
public object Uuids {
    public const val BYTES: Int = 16

    private const val VERSION_NIBBLE_MASK: Int = 0x0F
    private const val VARIANT_KEEP_MASK: Int = 0x3F
    private const val VERSION_5_BITS: Int = 0x50
    private const val VARIANT_RFC_BITS: Int = 0x80
    private const val VERSION_7_BITS: Long = 0x7000L
    private const val TIMESTAMP_SHIFT: Int = 16
    private const val RAND_A_MASK: Int = 0x0FFF
    private const val RAND_B_MASK: Long = 0x3FFF_FFFF_FFFF_FFFFL

    private val random = SecureRandom()

    /** RFC 9562 §5.5. */
    public fun v5(namespace: UUID, name: String): UUID {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(toBytes(namespace))
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val hash = sha1.digest().copyOf(BYTES)
        hash[6] = ((hash[6].toInt() and VERSION_NIBBLE_MASK) or VERSION_5_BITS).toByte()
        hash[8] = ((hash[8].toInt() and VARIANT_KEEP_MASK) or VARIANT_RFC_BITS).toByte()
        return read(ByteBuffer.wrap(hash))
    }

    /** RFC 9562 §5.7 with caller-supplied random fields. */
    public fun v7(unixMillis: Long, randA: Int, randB: Long): UUID {
        val msb = (unixMillis shl TIMESTAMP_SHIFT) or VERSION_7_BITS or (randA and RAND_A_MASK).toLong()
        val lsb = (randB and RAND_B_MASK) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }

    /** RFC 9562 §5.7 with random fields from a CSPRNG. */
    public fun v7(unixMillis: Long): UUID = v7(unixMillis, random.nextInt(), random.nextLong())

    /** A fresh random v4, for keys of things that are neither time-ordered nor named. */
    public fun random(): UUID = UUID.randomUUID()

    /** Unsigned byte-wise order, identical on every platform; used for every `(lamport, deviceId)` tie-break. */
    public fun compare(a: UUID, b: UUID): Int {
        val high = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }

    public fun toBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(BYTES).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    /** Reads 16 bytes; the caller guarantees they are present. */
    public fun read(buffer: ByteBuffer): UUID = UUID(buffer.long, buffer.long)

    public fun write(buffer: ByteBuffer, uuid: UUID) {
        buffer.putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
    }
}
