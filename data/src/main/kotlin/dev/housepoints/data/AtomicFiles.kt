package dev.housepoints.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Writes go to a sibling temp file and are moved into place atomically, so a crash never leaves half a file. */
internal object AtomicFiles {
    /** Throws [java.io.IOException] if the write or the move fails; the previous file is then untouched. */
    fun write(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
