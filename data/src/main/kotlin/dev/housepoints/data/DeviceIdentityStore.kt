package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.Uuids
import java.io.File
import java.util.UUID

/**
 * This installation's permanent identity (SPEC FR-2). It lives in `noBackupFilesDir`, which Auto Backup
 * always excludes, so a backup restored onto another phone gets a new identity rather than a copy.
 * An unreadable file is replaced with a fresh identity, which is always safe: the new identity simply
 * starts its own sequence.
 */
public class DeviceIdentityStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    public fun deviceId(): DeviceId = synchronized(lock) {
        read() ?: DeviceId(Uuids.random()).also { AtomicFiles.write(file, it.toString().toByteArray(Charsets.US_ASCII)) }
    }

    private fun read(): DeviceId? {
        if (!file.exists()) return null
        return runCatching { DeviceId(UUID.fromString(file.readText(Charsets.US_ASCII).trim())) }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "device-id"
        val lock = Any()
    }
}
