package com.sukashawarma.pos.data.update

import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator
import com.google.archivepatcher.shared.DefaultDeflater
import com.google.archivepatcher.shared.IDeflater
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.function.BiFunction

class ApkDeltaApplierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val deflaterFactory = BiFunction<Int, Boolean, IDeflater> { level, nowrap ->
        DefaultDeflater(level, nowrap)
    }

    @Test
    fun `archive patch reconstructs exact signed-style zip bytes`() {
        val base = zip("base.apk", "old payload ".repeat(5_000))
        val target = zip("target.apk", "new payload ".repeat(5_000))
        val patch = temporaryFolder.newFile("update.fbf")
        val compressor = Deflater(9, true)
        try {
            DeflaterOutputStream(FileOutputStream(patch), compressor, 32 * 1024).use { output ->
                FileByFileV1DeltaGenerator(deflaterFactory).generateDelta(base, target, output)
                output.finish()
            }
        } finally {
            compressor.end()
        }
        val reconstructed = File(temporaryFolder.root, "reconstructed.apk")

        val result = ApkDeltaApplier.apply(
            installedApk = base,
            patchFile = patch,
            outputApk = reconstructed,
            expectedBaseVersion = 8,
            expectedTargetVersion = 9,
            expectedTargetSha256 = ApkDeltaApplier.sha256(target)
        )

        assertEquals(ApkDeltaApplier.sha256(target), result.sha256)
        assertEquals(target.length(), result.bytesWritten)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong target hash is rejected`() {
        val base = zip("base.apk", "same")
        val target = zip("target.apk", "changed")
        val patch = temporaryFolder.newFile("update.fbf")
        val compressor = Deflater(9, true)
        try {
            DeflaterOutputStream(FileOutputStream(patch), compressor, 32 * 1024).use { output ->
                FileByFileV1DeltaGenerator(deflaterFactory).generateDelta(base, target, output)
                output.finish()
            }
        } finally {
            compressor.end()
        }
        ApkDeltaApplier.apply(base, patch, File(temporaryFolder.root, "bad.apk"), 8, 9, "00".repeat(32))
    }

    private fun zip(name: String, content: String): File = temporaryFolder.newFile(name).apply {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(this))).use { zip ->
            zip.setLevel(6)
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }
}
