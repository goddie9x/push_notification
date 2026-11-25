package com.god.notifypush

internal interface FileStorage {
    fun readText(fileName: String): String?
    fun writeText(fileName: String, text: String)
}

internal expect fun createFileStorage(directoryPath: String): FileStorage
