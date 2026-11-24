package hu.lextronic.radio.widget

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import hu.lextronic.radio.PlaybackService
import hu.lextronic.radio.Station
import hu.lextronic.radio.Store
import hu.lextronic.radio.toRadioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Rövid életű MediaController a lejátszó szolgáltatáshoz (a widget gombjai ezen át vezérelnek).
 *
 * FONTOS: az ActionCallback a broadcast receiver „korlátozott” Context-jét kapja, amivel tilos
 * szolgáltatáshoz kapcsolódni (bindService) — ezért az applicationContext-et használjuk.
 * Hiba esetén naplózunk és rövid üzenetet mutatunk, hogy ne nyelődjön el csendben.
 */
private suspend fun withController(context: Context, block: (MediaController) -> Unit) = withContext(Dispatchers.Main) {
    val ctx = context.applicationContext
    try {
        connectAndRun(ctx, block)
    } catch (e: Exception) {
        Log.e("LxWidget", "Widget-gomb hiba", e)
        Toast.makeText(ctx, "Lextronic: a lejátszó nem érhető el (${e.javaClass.simpleName})", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun connectAndRun(ctx: Context, block: (MediaController) -> Unit) {
    val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
    val future = MediaController.Builder(ctx, token).buildAsync()
    val c = suspendCancellableCoroutine<MediaController> { cont ->
        future.addListener({
            try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
        }, ContextCompat.getMainExecutor(ctx))
        cont.invokeOnCancellation { future.cancel(true) }
    }
    try {
        block(c)
        delay(500)          // a parancsok érjenek át, mielőtt lecsatlakozunk
    } finally { c.release() }
}

private fun startStation(c: MediaController, s: Station) {
    c.shuffleModeEnabled = false
    c.repeatMode = Player.REPEAT_MODE_OFF
    c.setMediaItem(s.toRadioItem())
    c.prepare()
    c.play()
}

/** Lejátszás / szünet. Élő adásnál a szünet leállítás (mint az appban), folytatáskor friss stream. */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val last = Store(context).last
        withController(context) { c ->
            val local = c.currentMediaItem?.mediaId?.startsWith("local:") == true
            when {
                c.isPlaying || (c.playWhenReady && c.playbackState != Player.STATE_IDLE) -> {
                    c.pause()
                    if (!local) c.stop()
                }
                c.mediaItemCount > 0 -> { c.prepare(); c.play() }
                last != null && !last.isLocal -> startStation(c, last)
            }
        }
    }
}

/** Előző / következő kedvenc (a „dir” paraméter: -1 vagy +1). */
class SkipFavAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = Store(context)
        val favs = store.loadFavs()
        if (favs.isEmpty()) return
        val dir = parameters[DIR] ?: 1
        val i = favs.indexOfFirst { it.url == store.last?.url }
        val s = favs[if (i < 0) 0 else (i + dir + favs.size) % favs.size]
        store.last = s
        withController(context) { c -> startStation(c, s) }
        LxWidgets.refresh(context.applicationContext, 0)
    }

    companion object { val DIR = ActionParameters.Key<Int>("dir") }
}

/** A szóló adó felvétele a kedvencek közé / törlése onnan. */
class ToggleFavAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = Store(context)
        val s = store.last ?: return
        if (s.isLocal) return
        val favs = store.loadFavs().toMutableList()
        val i = favs.indexOfFirst { it.url == s.url }
        if (i >= 0) favs.removeAt(i) else favs.add(s)
        store.saveFavs(favs)          // a megnyitott app ezt figyeli és frissíti a listáját
        LxWidgets.refresh(context.applicationContext, 0)
    }
}
