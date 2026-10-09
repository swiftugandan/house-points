package dev.housepoints.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Uuids
import dev.housepoints.sync.FamilyKey
import java.io.File
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the family key wrapped by an AES-256-GCM key that never leaves the Android Keystore (SAD §3.4).
 *
 * File (`noBackupFilesDir/family-key`): `0x01 ‖ familyId(16) ‖ ivLen u8 ‖ iv ‖ ciphertext`. The family id
 * is the associated data, so a key cannot be moved under another family's id.
 *
 * By design, a restored backup has the op log but no key: Keystore keys do not travel between devices,
 * and this file is not backed up. [load] then returns null and the app asks the parent to re-pair.
 */
public class FamilyKeyVault(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    public fun save(familyId: FamilyId, key: FamilyKey) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val familyBytes = Uuids.toBytes(familyId.uuid)
        cipher.updateAAD(familyBytes)
        val sealed = cipher.doFinal(key.bytes())
        val iv = cipher.iv
        val record = ByteBuffer.allocate(1 + familyBytes.size + 1 + iv.size + sealed.size)
            .put(FORMAT).put(familyBytes).put(iv.size.toByte()).put(iv).put(sealed)
            .array()
        AtomicFiles.write(file, record)
    }

    /** The stored family and key, or null when there is none or it can no longer be unwrapped. */
    public fun load(): Pair<FamilyId, FamilyKey>? {
        if (!file.exists()) return null
        return try {
            unwrap(ByteBuffer.wrap(file.readBytes()))
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: BufferUnderflowException) {
            null
        }
    }

    public fun clear() {
        file.delete()
    }

    private fun unwrap(buffer: ByteBuffer): Pair<FamilyId, FamilyKey>? {
        if (buffer.get() != FORMAT) return null
        val familyBytes = ByteArray(Uuids.BYTES).also { buffer.get(it) }
        val iv = ByteArray(buffer.get().toInt() and BYTE_MASK).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val wrapping = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrapping, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(familyBytes)
        val key = FamilyKey.of(cipher.doFinal(sealed)) ?: return null
        return FamilyId(Uuids.read(ByteBuffer.wrap(familyBytes))) to key
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "family-key"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hp-family-key-wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT: Byte = 0x01
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val BYTE_MASK = 0xFF
    }
}
