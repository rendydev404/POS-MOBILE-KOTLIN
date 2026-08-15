package com.sukashawarma.pos.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateManifestTest {
    @Test
    fun `selects delta matching installed base version`() {
        val manifest = Gson().fromJson(
            """
            {
              "version_code": 12,
              "version_name": "1.0.11",
              "apk_url": "https://example.test/app.apk",
              "apk_sha256": "abc",
              "apk_size_bytes": 14000000,
              "deltas": [
                {"base_version_code": 9, "patch_url": "https://example.test/9.fbf", "patch_sha256": "nine", "patch_size_bytes": 900000},
                {"base_version_code": 11, "patch_url": "https://example.test/11.fbf", "patch_sha256": "eleven", "patch_size_bytes": 110000}
              ]
            }
            """.trimIndent(),
            AppUpdateManifest::class.java
        )

        assertEquals("https://example.test/11.fbf", manifest.deltaFor(11)?.patchUrl)
        assertEquals(14_000_000L, manifest.apkSizeBytes)
        assertNull(manifest.deltaFor(10))
    }

    @Test
    fun `legacy single delta remains supported`() {
        val legacy = AppUpdateDelta(9, "https://example.test/patch.fbf", "hash", 123)
        val manifest = AppUpdateManifest(10, "1.0.9", "https://example.test/app.apk", delta = legacy)

        assertEquals(legacy, manifest.deltaFor(9))
    }
}
