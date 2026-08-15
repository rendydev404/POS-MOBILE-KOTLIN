package com.sukashawarma.pos.data.update

import com.google.archivepatcher.applier.FileByFileV1DeltaApplier
import com.google.archivepatcher.shared.DefaultDeflater
import com.google.archivepatcher.shared.IDeflater
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.function.BiFunction

/** Applies a deflate-compressed Google File-by-File v1 APK patch. */
internal object ApkDeltaApplier {
    private const val BUFFER_SIZE = 32 * 1024

    data class Result(val bytesWritten: Long, val sha256: String)

    private val deflaterFactory = BiFunction<Int, Boolean, IDeflater> { level, nowrap ->
        DefaultDeflater(level, nowrap)
    }

    fun apply(
        installedApk: File,
        patchFile: File,
        outputApk: File,
        expectedBaseVersion: Int,
        expectedTargetVersion: Int,
        expectedTargetSha256: String
    ): Result {
        require(installedApk.isFile) { "Installed APK is unavailable" }
        require(patchFile.isFile) { "Delta patch is unavailable" }
        require(expectedBaseVersion > 0 && expectedTargetVersion > expectedBaseVersion) {
            "Invalid delta version range"
        }
        val partial = File(outputApk.parentFile, "${outputApk.name}.partial")
        partial.delete()
        val inflater = Inflater(true)
        try {
            BufferedInputStream(FileInputStream(patchFile), BUFFER_SIZE).use { compressedPatch ->
                InflaterInputStream(compressedPatch, inflater, BUFFER_SIZE).use { patch ->
                    BufferedOutputStream(FileOutputStream(partial), BUFFER_SIZE).use { output ->
                        FileByFileV1DeltaApplier(outputApk.parentFile, deflaterFactory).applyDelta(
                            installedApk,
                            patch,
                            output
                        )
                    }
                }
            }
            val actualHash = sha256(partial)
            require(actualHash.equals(expectedTargetSha256, ignoreCase = true)) {
                "Reconstructed APK SHA-256 mismatch"
            }
            if (outputApk.exists()) require(outputApk.delete()) { "Cannot replace old APK" }
            require(partial.renameTo(outputApk)) { "Cannot finalize reconstructed APK" }
            return Result(outputApk.length(), actualHash)
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            inflater.end()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
