package dev.housepoints.sync

/** The 256-bit secret shared by every phone in a family (SPEC FR-3). Never printed. */
public class FamilyKey private constructor(private val material: ByteArray) {
    public fun bytes(): ByteArray = material.copyOf()

    override fun toString(): String = "FamilyKey(redacted)"

    public companion object {
        public const val BYTES: Int = 32

        public fun generate(): FamilyKey = TODO("red")

        public fun of(bytes: ByteArray): FamilyKey? = TODO("red")
    }
}
