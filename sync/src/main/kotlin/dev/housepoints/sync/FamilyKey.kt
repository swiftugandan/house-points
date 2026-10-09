package dev.housepoints.sync

import java.security.SecureRandom

/** The 256-bit secret shared by every phone in a family (SPEC FR-3). Never printed. */
public class FamilyKey private constructor(private val material: ByteArray) {
    public fun bytes(): ByteArray = material.copyOf()

    override fun toString(): String = "FamilyKey(redacted)"

    public companion object {
        public const val BYTES: Int = 32

        private val random = SecureRandom()

        public fun generate(): FamilyKey = FamilyKey(ByteArray(BYTES).also(random::nextBytes))

        /** Null unless [bytes] is exactly [BYTES] long. */
        public fun of(bytes: ByteArray): FamilyKey? = if (bytes.size == BYTES) FamilyKey(bytes.copyOf()) else null
    }
}
