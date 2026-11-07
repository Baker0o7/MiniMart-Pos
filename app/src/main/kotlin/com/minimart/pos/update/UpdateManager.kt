package com.minimart.pos.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import com.minimart.pos.BuildConfig
import com.minimart.pos.util.AppUpdate
import com.minimart.pos.util.ReleaseAsset
import com.minimart.pos.util.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val checkedAt: Long) : UpdateState()
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateState()
    data class Downloading(val release: ReleaseInfo, val percent: Int) : UpdateState()
    /** The file is downloaded and checked and handed to Android, which asks the owner to confirm. */
    data class Installing(val release: ReleaseInfo) : UpdateState()
    data class NeedsPermission(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateState()
    data class Failed(val message: String, val release: ReleaseInfo? = null, val asset: ReleaseAsset? = null) : UpdateState()
}

/**
 * Looks for a newer release of this app on its own GitHub page, downloads the right APK, checks it, and gives it
 * to Android's installer, which shows the usual "Update this app?" prompt.
 *
 * Safety: only the project's own release address is contacted ([AppUpdate.isTrustedUrl], also for every redirect);
 * the download is checked against the size and, when GitHub publishes one, the SHA-256 checksum; and Android itself
 * refuses to install an update that was not signed with the same key as the installed app, so a swapped file cannot
 * replace the app. Nothing about the shop or the owner is sent: the request carries no account, no email and no ID.
 */
@Singleton
class UpdateManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val busy = Mutex()
    private val prefs by lazy { context.getSharedPreferences("updates", Context.MODE_PRIVATE) }

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto_check", true)
        set(v) { prefs.edit().putBoolean("auto_check", v).apply() }

    /** Checks at most once a day, quietly, and only when the owner has not turned it off. */
    suspend fun autoCheckIfDue(now: Long = System.currentTimeMillis()) {
        if (!autoCheck || now - prefs.getLong("checked_at", 0L) < DAY_MS) return
        check()
    }

    suspend fun check() {
        if (!busy.tryLock()) return
        try {
            _state.value = UpdateState.Checking
            _state.value = try {
                val release = withContext(Dispatchers.IO) { fetchLatest() }
                prefs.edit().putLong("checked_at", System.currentTimeMillis()).apply()
                val asset = release?.let { AppUpdate.pickAsset(it.assets, Build.SUPPORTED_ABIS.toList()) }
                if (release != null && asset != null && AppUpdate.isNewer(BuildConfig.VERSION_NAME, release.tag)) UpdateState.Available(release, asset)
                else UpdateState.UpToDate(System.currentTimeMillis())
            } catch (e: Exception) {
                UpdateState.Failed("Could not check for updates. Check the internet connection and try again.")
            }
        } finally { busy.unlock() }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The Android screen where the owner allows this app to install updates. */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun install(release: ReleaseInfo, asset: ReleaseAsset) {
        if (!busy.tryLock()) return
        try {
            if (!canInstall()) { _state.value = UpdateState.NeedsPermission(release, asset); return }
            _state.value = UpdateState.Downloading(release, 0)
            val file = withContext(Dispatchers.IO) { download(release, asset) }
            withContext(Dispatchers.IO) { handOver(file) }
            _state.value = UpdateState.Installing(release)
        } catch (e: UpdateException) {
            _state.value = UpdateState.Failed(e.message ?: "The update could not be installed.", release, asset)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("The update could not be downloaded. Check the internet connection and try again.", release, asset)
        } finally { busy.unlock() }
    }

    /** Called by the install receiver when Android reports a problem. */
    fun installFailed(message: String) { _state.update { UpdateState.Failed(message) } }

    // ── network ──────────────────────────────────────────────────────────────

    private fun fetchLatest(): ReleaseInfo? {
        val conn = URL(AppUpdate.API_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 15_000; conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MiniMartPOS-updater")
            if (conn.responseCode != 200) throw UpdateException("The update server answered ${conn.responseCode}.")
            val body = conn.inputStream.use { it.readBytes(MAX_JSON) }.toString(Charsets.UTF_8)
            return ReleaseParser.parse(body)
        } finally { conn.disconnect() }
    }

    private fun download(release: ReleaseInfo, asset: ReleaseAsset): File {
        if (!AppUpdate.isTrustedUrl(asset.url)) throw UpdateException("The update address is not one this app trusts.")
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        if (!AppUpdate.enoughSpace(StatFs(dir.path).availableBytes, asset.size))
            throw UpdateException("Not enough free space on the phone for the update. Free up about ${asset.size * 2 / 1_000_000 + 50} MB and try again.")
        val out = File(dir, "update.apk")
        var url = asset.url; var redirected = false; var hops = 0
        while (true) {
            if (!AppUpdate.isTrustedUrl(url, redirected)) throw UpdateException("The update was redirected to an address this app does not trust.")
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000; conn.readTimeout = 30_000; conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "MiniMartPOS-updater")
                val code = conn.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    url = URL(URL(url), conn.getHeaderField("Location") ?: throw UpdateException("The update server sent a bad redirect.")).toString()
                    redirected = true
                    if (++hops > 5) throw UpdateException("The update server redirected too many times.")
                    continue
                }
                if (code != 200) throw UpdateException("The update server answered $code.")
                val digest = MessageDigest.getInstance("SHA-256"); var done = 0L; var lastPercent = -1
                conn.inputStream.use { input -> out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        done += n; if (done > asset.size) throw UpdateException("The downloaded file is larger than expected, so it was discarded.")
                        o.write(buf, 0, n); digest.update(buf, 0, n)
                        val pct = (done * 100 / asset.size).toInt()
                        if (pct != lastPercent) { lastPercent = pct; _state.value = UpdateState.Downloading(release, pct) }
                    }
                    o.fd.sync()
                } }
                if (done != asset.size) { out.delete(); throw UpdateException("The download was cut short. Please try again.") }
                val expected = asset.sha256
                if (expected != null && digest.digest().joinToString("") { "%02x".format(it) } != expected) {
                    out.delete(); throw UpdateException("The downloaded file did not match its checksum, so it was discarded.")
                }
                return out
            } finally { conn.disconnect() }
        }
    }

    /** Streams the checked file into Android's package installer and commits; Android then shows its confirmation. */
    private fun handOver(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input -> session.openWrite("update.apk", 0, file.length()).use { o -> input.copyTo(o); session.fsync(o) } }
            val cb = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, cb, flags).intentSender)
        }
    }

    class UpdateException(message: String) : Exception(message)

    private fun java.io.InputStream.readBytes(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (true) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) throw UpdateException("The update information was too large.") }
        return out.toByteArray()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_JSON = 512 * 1024
    }
}

/** Reads GitHub's "latest release" JSON into a [ReleaseInfo]; drafts, pre-releases and odd tags give null. */
object ReleaseParser {
    fun parse(json: String): ReleaseInfo? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name").takeIf { AppUpdate.parseVersion(it) != null } ?: return null
        val arr = o.optJSONArray("assets") ?: return null
        val assets = (0 until minOf(arr.length(), 40)).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = a.optString("browser_download_url"); val name = a.optString("name")
            if (name.isBlank() || url.isBlank()) return@mapNotNull null
            ReleaseAsset(name, url, a.optLong("size"), AppUpdate.sha256Of(a.optString("digest")))
        }
        return ReleaseInfo(tag, AppUpdate.cleanNotes(o.optString("body")), assets)
    }
}
