package com.hexa.core.persist

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe text file: writes go to a temporary file which is fsynced and atomically renamed, so a
 * reboot or crash mid-write leaves either the old or the new content, never a truncated file.
 * Blocking; call from an IO dispatcher.
 */
class AtomicTextFile(val file: File) {

    fun readOrNull(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: IOException) {
        null
    }

    fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Keeps an unreadable file aside instead of overwriting it, so it can be inspected/recovered. */
    fun quarantine() {
        if (file.exists()) file.renameTo(File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis()))
    }
}
