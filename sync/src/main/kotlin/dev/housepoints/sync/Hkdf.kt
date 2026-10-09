package dev.housepoints.sync

/** RFC 5869 HKDF with HMAC-SHA256 [SAD R17]. */
internal object Hkdf {
    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray = TODO("red")
}
