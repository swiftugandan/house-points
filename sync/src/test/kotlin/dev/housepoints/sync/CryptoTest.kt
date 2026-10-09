package dev.housepoints.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `HKDF matches RFC 5869 test case 1`() {
        val okm = Hkdf.derive(
            ikm = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm,
        )
    }

    @Test
    fun `family keys are exactly 32 bytes and never printed`() {
        assertNull(FamilyKey.of(ByteArray(31)))
        val key = FamilyKey.generate()
        assertEquals(32, key.bytes().size)
        assertNotNull(FamilyKey.of(key.bytes()))
        assertEquals("FamilyKey(redacted)", key.toString())
    }

    @Test
    fun `sealed frames open on the other side in order`() {
        val key = ByteArray(32) { it.toByte() }
        val alice = FrameCipher(key, sendDirection = 1, receiveDirection = 2)
        val bob = FrameCipher(key, sendDirection = 2, receiveDirection = 1)
        val first = alice.seal(3, "hello".toByteArray())
        val second = alice.seal(4, "world".toByteArray())
        assertEquals(3.toByte(), bob.open(first).first)
        assertArrayEquals("world".toByteArray(), bob.open(second).second)
    }

    @Test
    fun `a replayed frame is rejected because the counter has moved on`() {
        val key = ByteArray(32) { 7 }
        val alice = FrameCipher(key, sendDirection = 1, receiveDirection = 2)
        val bob = FrameCipher(key, sendDirection = 2, receiveDirection = 1)
        val frame = alice.seal(3, byteArrayOf(1, 2, 3))
        bob.open(frame)
        assertThrows(AEADBadTagException::class.java) { bob.open(frame) }
    }

    @Test
    fun `a flipped bit is detected`() {
        val key = ByteArray(32) { 9 }
        val alice = FrameCipher(key, sendDirection = 1, receiveDirection = 2)
        val bob = FrameCipher(key, sendDirection = 2, receiveDirection = 1)
        val frame = alice.seal(3, ByteArray(40))
        frame[frame.size - 20] = (frame[frame.size - 20].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { bob.open(frame) }
    }

    @Test
    fun `a frame cannot be reflected back to its sender`() {
        val key = ByteArray(32) { 5 }
        val alice = FrameCipher(key, sendDirection = 1, receiveDirection = 2)
        val frame = alice.seal(3, byteArrayOf(1))
        assertThrows(AEADBadTagException::class.java) { alice.open(frame) }
    }
}
