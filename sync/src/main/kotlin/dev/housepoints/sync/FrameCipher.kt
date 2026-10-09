package dev.housepoints.sync

/** Seals and opens post-authentication frames with AES-256-GCM and implicit per-direction counters. */
internal class FrameCipher(key: ByteArray, private val sendDirection: Byte, private val receiveDirection: Byte) {
    fun seal(type: Byte, body: ByteArray): ByteArray = TODO("red")

    /** Returns the frame's type and plaintext body; throws [javax.crypto.AEADBadTagException] when tampered. */
    fun open(frame: ByteArray): Pair<Byte, ByteArray> = TODO("red")
}
