package com.minimart.pos.util

import java.net.URI

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)
data class ReleaseInfo(val tag: String, val notes: String, val assets: List<ReleaseAsset>)

/**
 * The rules of the in-app updater, kept free of Android so they can be tested: which version is newer, which
 * download addresses are trusted, which APK suits the phone, and what a release's checksum looks like.
 * Everything that comes from the network is treated as untrusted: an address outside the project's own GitHub
 * release area is never downloaded, whatever the release page says.
 */
object AppUpdate {
    const val REPO = "Baker0o7/MiniMart-Pos"
    const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASE_PATH = "/$REPO/releases/download/"
    /** GitHub serves release files from github.com and then redirects to its own download hosts. */
    private val DOWNLOAD_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** "v1.28.0" or "1.28.0-rc1" -> [1, 28, 0]; null when it is not a version number. */
    fun parseVersion(text: String): List<Int>? {
        val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        val nums = parts.map { p -> if (p.isNotEmpty() && p.length <= 6 && p.all { it in '0'..'9' }) p.toInt() else return null }
        return nums
    }

    fun isNewer(current: String, latest: String): Boolean {
        val a = parseVersion(current) ?: return false
        val b = parseVersion(latest) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    /** True for the project's own release file on github.com, or GitHub's download hosts it redirects to. */
    fun isTrustedUrl(url: String, redirected: Boolean = false): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) && when (u.host?.lowercase()) {
            "github.com" -> !redirected && u.path.startsWith(RELEASE_PATH) && !u.path.contains("..")
            in DOWNLOAD_HOSTS -> redirected
            else -> false
        }
    } catch (_: Exception) { false }

    /** The APK for the phone's own processor when there is one, else the universal APK. Never the Play bundle. */
    fun pickAsset(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && isTrustedUrl(it.url) && it.size > 0 }
        for (abi in abis) apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
    }

    /** GitHub writes a file's checksum as "sha256:<64 hex>". Anything else is treated as "no checksum". */
    fun sha256Of(digest: String?): String? {
        val hex = digest?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** Release notes for a shop owner: plain text, no markdown symbols, short. */
    fun cleanNotes(notes: String, max: Int = 700): String {
        val t = notes.replace("\r", "").lines().map { it.trim().trimStart('#', '*', '-', ' ').replace("**", "").replace("`", "") }
            .filter { it.isNotBlank() }.joinToString("\n")
        return if (t.length <= max) t else t.take(max).trimEnd() + "..."
    }

    /** Space needed to download and then hand the file to the installer. */
    fun enoughSpace(freeBytes: Long, apkBytes: Long): Boolean = freeBytes >= apkBytes * 2 + 50L * 1024 * 1024
}
