package hu.lextronic.radio.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import hu.lextronic.radio.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** A widgetek állapotkulcsai (Glance Preferences). */
internal object W {
    val LINE1 = stringPreferencesKey("line1")       // adó neve (helyi zenénél a szám címe)
    val LINE2 = stringPreferencesKey("line2")       // ICY számcím (helyi zenénél az előadó)
    val INFO = stringPreferencesKey("info")         // „MP3 128k” / „Helyi zene”
    val PLAYING = booleanPreferencesKey("playing")
    val FAV = booleanPreferencesKey("fav")
    val CAN_FAV = booleanPreferencesKey("canFav")
    val EQ = stringPreferencesKey("eq")             // 5 érték dB-ben, vesszővel
    val EQ_ON = booleanPreferencesKey("eqOn")
    val LOGO_VER = longPreferencesKey("logoVer")    // 0 = nincs logó
}

/**
 * A két widget frissítése. Az adatokat a lejátszó (PlaybackService) és az app (hangszín,
 * kedvencek) jelzi ide; rövid késleltetéssel összevonva írjuk ki, hogy egy húzás a
 * hangszínszabályzón ne indítson tucatnyi widget-frissítést.
 */
object LxWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    internal fun logoFile(ctx: Context) = File(ctx.filesDir, "widget_logo.png")

    @Synchronized
    fun refresh(ctx: Context, delayMs: Long = 300) {
        val app = ctx.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            try { refreshNow(app) } catch (e: Exception) { }
        }
    }

    /** A lejátszó állapotváltozásai (a szolgáltatás hívja, fő szálon). */
    fun onPlayer(ctx: Context, p: Player) {
        val playing = p.playWhenReady && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED
        val m = p.mediaMetadata
        sp(ctx).edit()
            .putString("np_id", p.currentMediaItem?.mediaId.orEmpty())
            .putString("np_title", m.title?.toString().orEmpty())
            .putString("np_artist", m.artist?.toString().orEmpty())
            .putBoolean("np_playing", playing)
            .apply()
        refresh(ctx)
    }

    fun onStopped(ctx: Context) {
        sp(ctx).edit().putBoolean("np_playing", false).apply()
        refresh(ctx, 0)
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("lextronic", Context.MODE_PRIVATE)

    private suspend fun refreshNow(ctx: Context) {
        val mgr = GlanceAppWidgetManager(ctx)
        val ids = mgr.getGlanceIds(CompactWidget::class.java) + mgr.getGlanceIds(PanelWidget::class.java)
        if (ids.isEmpty()) return

        val sp = sp(ctx)
        val store = Store(ctx)
        val last = store.last
        val npId = sp.getString("np_id", "").orEmpty()
        val npTitle = sp.getString("np_title", "").orEmpty()
        val local = npId.startsWith("local:") || (npId.isEmpty() && last?.isLocal == true)

        val line1: String
        val line2: String
        val info: String
        if (local) {
            line1 = npTitle.ifBlank { last?.name ?: "Helyi zene" }
            line2 = sp.getString("np_artist", "").orEmpty().takeIf { it != "<unknown>" }.orEmpty()
            info = "HELYI ZENE"
        } else {
            line1 = last?.name ?: "Lextronic Rádió"
            line2 = npTitle.takeIf { it != line1 }.orEmpty()
            info = last?.let { s ->
                listOf(s.codec.uppercase(), if (s.bitrate > 0) "${s.bitrate}k" else "").filter { it.isNotBlank() }.joinToString(" ")
            }.orEmpty()
        }
        val fav = !local && last != null && store.loadFavs().any { it.url == last.url }

        // logó: csak akkor töltjük le újra, ha másik adóra váltottunk
        val logoUrl = if (local) "" else last?.favicon.orEmpty().takeIf { it.startsWith("http") }.orEmpty()
        var logoVer = sp.getLong("w_logo_ver", 0L)
        if (logoUrl != sp.getString("w_logo_url", null)) {
            val bmp = if (logoUrl.isNotEmpty()) loadBitmap(ctx, logoUrl) else null
            val f = logoFile(ctx)
            logoVer = if (bmp != null) {
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                System.currentTimeMillis()
            } else { f.delete(); 0L }
            sp.edit().putString("w_logo_url", logoUrl).putLong("w_logo_ver", logoVer).apply()
        }

        val eq = store.eqGains.joinToString(",")
        val eqOn = store.eqOn
        val playing = sp.getBoolean("np_playing", false)

        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { p ->
                p[W.LINE1] = line1
                p[W.LINE2] = line2
                p[W.INFO] = info
                p[W.PLAYING] = playing
                p[W.FAV] = fav
                p[W.CAN_FAV] = !local && last != null
                p[W.EQ] = eq
                p[W.EQ_ON] = eqOn
                p[W.LOGO_VER] = logoVer
            }
        }
        CompactWidget().updateAll(ctx)
        PanelWidget().updateAll(ctx)
    }

    private suspend fun loadBitmap(ctx: Context, url: String): Bitmap? = try {
        val r = ctx.imageLoader.execute(
            ImageRequest.Builder(ctx).data(url).size(128).allowHardware(false).build()
        )
        (r as? SuccessResult)?.drawable?.toBitmap()
    } catch (e: Exception) { null }
}
