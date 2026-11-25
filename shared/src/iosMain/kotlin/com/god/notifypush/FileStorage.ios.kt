package com.god.notifypush

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun createFileStorage(directoryPath: String): FileStorage =
    IosFileStorage(directoryPath)

@OptIn(ExperimentalForeignApi::class)
internal class IosFileStorage(private val directoryPath: String) : FileStorage {
    private fun directory(): String {
        if (directoryPath.isNotEmpty()) return directoryPath
        return NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
            null,
            false,
            null,
        )?.path ?: ""
    }

    override fun readText(fileName: String): String? {
        val path = (directory() as NSString).stringByAppendingPathComponent(fileName)
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    }

    override fun writeText(fileName: String, text: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(directory(), true, null, null)
        val path = (directory() as NSString).stringByAppendingPathComponent(fileName)
        (text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    }
}
