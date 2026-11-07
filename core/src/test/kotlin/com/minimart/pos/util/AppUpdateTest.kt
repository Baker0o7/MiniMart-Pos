package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppUpdateTest {
    private val base = "https://github.com/Baker0o7/MiniMart-Pos/releases/download/v1.29.0/"
    private fun a(name: String, size: Long = 20_000_000, url: String = base + name) = ReleaseAsset(name, url, size, null)

    @Test fun versionsCompareNumerically() {
        assertTrue(AppUpdate.isNewer("1.28.0", "1.28.1")); assertTrue(AppUpdate.isNewer("1.9.0", "1.10.0"))
        assertTrue(AppUpdate.isNewer("1.28.0", "v2.0")); assertTrue(AppUpdate.isNewer("1.28.0-debug", "1.28.1"))
        assertFalse(AppUpdate.isNewer("1.28.0", "1.28.0")); assertFalse(AppUpdate.isNewer("1.28.1", "1.28.0"))
        assertFalse(AppUpdate.isNewer("1.28", "1.28.0"))
    }
    @Test fun junkIsNeverNewer() {
        listOf("", "latest", "1.x", "1..2", "-1.0", "99999999.0", "1.2.3.4.5").forEach { assertFalse(AppUpdate.isNewer("1.0.0", it), it) }
        assertFalse(AppUpdate.isNewer("junk", "2.0.0"))
    }
    @Test fun onlyTheProjectsOwnReleaseFilesAreTrusted() {
        assertTrue(AppUpdate.isTrustedUrl(base + "app-universal-release.apk"))
        assertFalse(AppUpdate.isTrustedUrl("http://github.com/Baker0o7/MiniMart-Pos/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://github.com/Someone/Else/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://github.com.evil.com/Baker0o7/MiniMart-Pos/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://github.com@evil.com/Baker0o7/MiniMart-Pos/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://evil.com/Baker0o7/MiniMart-Pos/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://github.com:8443/Baker0o7/MiniMart-Pos/releases/download/v1/app.apk"))
        assertFalse(AppUpdate.isTrustedUrl("https://github.com/Baker0o7/MiniMart-Pos/releases/download/../../evil"))
        assertFalse(AppUpdate.isTrustedUrl("file:///sdcard/app.apk")); assertFalse(AppUpdate.isTrustedUrl("not a url"))
    }
    @Test fun redirectsMayOnlyGoToGitHubsDownloadHosts() {
        assertTrue(AppUpdate.isTrustedUrl("https://objects.githubusercontent.com/x/y?z=1", redirected = true))
        assertTrue(AppUpdate.isTrustedUrl("https://release-assets.githubusercontent.com/x", redirected = true))
        assertFalse(AppUpdate.isTrustedUrl("https://objects.githubusercontent.com/x"))                 // not as a first address
        assertFalse(AppUpdate.isTrustedUrl("https://evil.com/x", redirected = true))
        assertFalse(AppUpdate.isTrustedUrl(base + "a.apk", redirected = true))                          // no bouncing back to github.com
    }
    @Test fun thePhonesOwnProcessorWinsThenUniversal() {
        val all = listOf(a("app-arm64-v8a-release.apk"), a("app-armeabi-v7a-release.apk"), a("app-x86_64-release.apk"), a("app-universal-release.apk"), a("app-release.aab"))
        assertEquals("app-arm64-v8a-release.apk", AppUpdate.pickAsset(all, listOf("arm64-v8a", "armeabi-v7a"))!!.name)
        assertEquals("app-armeabi-v7a-release.apk", AppUpdate.pickAsset(all, listOf("armeabi-v7a"))!!.name)
        assertEquals("app-universal-release.apk", AppUpdate.pickAsset(all, listOf("riscv64"))!!.name)
    }
    @Test fun bundlesUntrustedAndEmptyFilesAreNeverPicked() {
        assertNull(AppUpdate.pickAsset(listOf(a("app-release.aab")), listOf("arm64-v8a")))
        assertNull(AppUpdate.pickAsset(listOf(a("app-arm64-v8a-release.apk", url = "https://evil.com/a.apk")), listOf("arm64-v8a")))
        assertNull(AppUpdate.pickAsset(listOf(a("app-arm64-v8a-release.apk", size = 0)), listOf("arm64-v8a")))
    }
    @Test fun checksumsAreParsedStrictly() {
        val hex = "a".repeat(64)
        assertEquals(hex, AppUpdate.sha256Of("sha256:$hex")); assertEquals(hex, AppUpdate.sha256Of(hex.uppercase()))
        assertNull(AppUpdate.sha256Of(null)); assertNull(AppUpdate.sha256Of("sha256:abc")); assertNull(AppUpdate.sha256Of("md5:" + "a".repeat(32))); assertNull(AppUpdate.sha256Of("sha256:" + "z".repeat(64)))
    }
    @Test fun notesAreShortPlainText() {
        val n = AppUpdate.cleanNotes("## [1.29.0] - 2026-11-08\n- **Light theme.** Pale colours.\n\n- `Fix` thing\n")
        assertFalse(n.contains("*") || n.contains("#") || n.contains("`")); assertTrue(n.contains("Light theme. Pale colours."))
        assertTrue(AppUpdate.cleanNotes("x".repeat(5000)).length <= 703)
    }
    @Test fun downloadNeedsRoomForTheFileTwiceOver() {
        assertTrue(AppUpdate.enoughSpace(300L * 1024 * 1024, 20L * 1024 * 1024)); assertFalse(AppUpdate.enoughSpace(60L * 1024 * 1024, 20L * 1024 * 1024))
    }
}
