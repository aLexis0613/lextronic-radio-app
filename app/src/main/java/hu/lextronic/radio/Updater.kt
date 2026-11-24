package hu.lextronic.radio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub „latest” kiadás adatai (a CI írja a version.json-ba). */
data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: String)

/**
 * Frissítés a GitHub Releases-ből.
 *
 * A CI minden push után a `latest` kiadásba tölti fel a LextronicRadio.apk-t és mellé egy
 * version.json-t. Az app induláskor letölti a version.json-t; ha a versionCode nagyobb a
 * telepítettnél, felajánlja a frissítést: letölti az APK-t és átadja a rendszer telepítőjének
 * (a telepítést a felhasználónak mindig jóvá kell hagynia).
 */
object Updater {
    private const val BASE = "https://github.com/aLexis0613/lextronic-radio-app/releases/download/latest/"
    private const val APK_URL = BASE + "LextronicRadio.apk"
    private const val INFO_URL = BASE + "version.json"

    fun installedVersion(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun installedVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    const val REPO_URL = "https://github.com/aLexis0613/lextronic-radio-app"
    const val RELEASE_URL = "$REPO_URL/releases/tag/latest"

    /** Hálózati szálon hívandó. Hiba esetén null (nincs net, épp újraépül a kiadás stb.). */
    fun fetchInfo(): UpdateInfo? {
        val s = httpGet(INFO_URL, 8000) ?: return null
        return try {
            val o = JSONObject(s)
            UpdateInfo(o.getLong("versionCode"), o.optString("versionName"), o.optString("notes").trim())
        } catch (e: Exception) { null }
    }

    /** Letölti az APK-t a cache-be. Hálózati szálon hívandó; [progress] 0..100. Hibánál null. */
    fun download(ctx: Context, progress: (Int) -> Unit): File? {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "LextronicRadio.apk")
        return try {
            val c = URL(APK_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            try {
                if (c.responseCode !in 200..299) return null
                val total = c.contentLengthLong
                var done = 0L
                var last = -1
                c.inputStream.use { inp ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) { last = p; progress(p) }
                            }
                        }
                    }
                }
            } finally { c.disconnect() }
            out
        } catch (e: Exception) { out.delete(); null }
    }

    /**
     * Ugyanazzal a kulccsal van-e aláírva a letöltött APK, mint a telepített app.
     * Ha nem, a rendszer telepítője csak egy semmitmondó „nem telepíthető” hibát adna.
     * Ha nem tudjuk eldönteni, true (a telepítő majd szól).
     */
    fun signatureMatches(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true
        val pm = ctx.packageManager
        val info: PackageInfo = (try {
            if (Build.VERSION.SDK_INT >= 33)
                pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            else @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: Exception) { null }) ?: return true
        if (info.packageName != ctx.packageName) return false
        val signers = info.signingInfo?.apkContentsSigners ?: return true
        return signers.any { pm.hasSigningCertificate(ctx.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509) }
    }

    /** Engedélyezte-e már a felhasználó, hogy ez az app telepíthessen („ismeretlen források”). */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
