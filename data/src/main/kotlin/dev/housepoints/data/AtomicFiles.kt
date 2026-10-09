package dev.housepoints.data

import java.io.File

/** Writes go to a sibling temp file and are renamed into place, so a crash never leaves half a file. */
internal object AtomicFiles {
    fun write(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }
}
