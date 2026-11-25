package com.god.notifypush

import java.io.File

internal actual fun createFileStorage(directoryPath: String): FileStorage =
    JvmFileStorage(File(directoryPath))

internal class JvmFileStorage(private val directory: File) : FileStorage {
    override fun readText(fileName: String): String? {
        val file = File(directory, fileName)
        return if (file.exists()) file.readText() else null
    }

    override fun writeText(fileName: String, text: String) {
        directory.mkdirs()
        File(directory, fileName).writeText(text)
    }
}
