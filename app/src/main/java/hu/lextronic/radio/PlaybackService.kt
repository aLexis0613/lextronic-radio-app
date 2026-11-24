package hu.lextronic.radio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import hu.lextronic.radio.widget.LxWidgets

/**
 * Háttérlejátszás: a hang akkor is szól, ha az app nincs előtérben vagy a képernyő le van zárva.
 * Értesítés és lezárási képernyő-vezérlők a Media3-tól jönnek.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var focus: RadioFocus? = null
    private var live: LiveReconnect? = null

    override fun onCreate() {
        super.onCreate()
        // a hangszínszabályzó beállításai akkor is érvényesek, ha az appot nem nyitották meg
        // (pl. a widgetről vagy az értesítésből indul a lejátszás)
        Store(this).let { AudioFx.setGains(it.eqGains, it.eqOn) }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)      // sok állomás http -> https-re irányít át
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val sources = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(DefaultDataSource.Factory(this, http))
        // netrádióhoz nagyobb puffer: mobilneten kevesebb akadás
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 90_000, 2_500, 5_000)
            .build()
        // saját hangfeldolgozó a hangútban: hangszínszabályzó + spektrum.
        // (Emiatt a hardveres „offload” dekódolás nem használható — a különbség kicsi.)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(LxAudioProcessor()))
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false   // az audio focust a RadioFocus kezeli (ld. lent), nem az ExoPlayer
            )
            .setHandleAudioBecomingNoisy(true)          // fülhallgató kihúzásakor megáll
            .setWakeMode(C.WAKE_MODE_NETWORK)          // lezárt képernyőnél is fut a WiFi/mobilnet
            .build()

        // A MediaItem saját címe felülírná a stream ICY-címét, ezért az érkező számcímet
        // beleírjuk a MediaItem-be: így az app, az értesítés és a lezárási képernyő is látja.
        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val e = metadata.get(i)
                    if (e !is IcyInfo) continue
                    val cur = player.currentMediaItem ?: return
                    val station = cur.mediaMetadata.station?.toString()
                        ?: cur.mediaMetadata.artist?.toString() ?: ""
                    val newTitle = e.title?.trim().orEmpty().ifBlank { station }
                    if (cur.mediaMetadata.title?.toString() == newTitle) return
                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        cur.buildUpon().setMediaMetadata(cur.mediaMetadata.buildUpon().setTitle(newTitle).build()).build()
                    )
                }
            }
        })

        focus = RadioFocus(this, player)
        // a kezdőképernyő-widgetek követik a lejátszót
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION
                    )
                ) LxWidgets.onPlayer(this@PlaybackService, p)
            }
        })
        live = LiveReconnect(this, player)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        LxWidgets.onStopped(this)
        focus?.release()
        focus = null
        live?.release()
        live = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Élő adás frissen tartása (csak netrádiónál, helyi zenénél nem):
 *  - hosszabb szünet után folytatáskor újracsatlakozik, így nem a pufferből, percekkel lemaradva
 *    szól tovább (és nem akad el azon, hogy a szerver közben bontotta a kapcsolatot);
 *  - ha a telefon hálózatot vált (WiFi <-> mobilnet), azonnal újracsatlakozik az új hálózaton,
 *    nem várja meg, hogy a régi kapcsolat elhaljon. Hiba után ugyanígy újraindítja a lejátszást.
 */
@OptIn(UnstableApi::class)
private class LiveReconnect(context: Context, private val player: ExoPlayer) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var pausedAt = 0L
    private var network: Network? = null

    private fun isLive(): Boolean {
        val scheme = player.currentMediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun reconnect() {
        if (!isLive()) return
        player.stop()
        player.prepare()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) { pausedAt = SystemClock.elapsedRealtime(); return }
            val paused = pausedAt
            pausedAt = 0L
            if (paused > 0 && SystemClock.elapsedRealtime() - paused > RESUME_FRESH_MS &&
                player.playbackState != Player.STATE_IDLE) reconnect()
        }
        override fun onPlaybackStateChanged(state: Int) {
            // leállított (stop) lejátszó úgyis frissen csatlakozik újra: nincs mit pótolni
            if (state == Player.STATE_IDLE) pausedAt = 0L
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            val old = network
            network = n
            // az első hívás a feliratkozáskor jön a meglévő hálózatra: az nem váltás
            if (old == null || old == n) return
            if (!player.playWhenReady || player.mediaItemCount == 0) return
            // kis késleltetés: váltáskor a DNS/útvonal még pár száz ms-ig rendeződik
            main.removeCallbacks(netReconnect)
            main.postDelayed(netReconnect, 800)
        }
    }
    // hiba után (STATE_IDLE + playWhenReady) az új hálózaton magától is újraindul
    private val netReconnect = Runnable { if (player.playWhenReady) reconnect() }

    init {
        player.addListener(playerListener)
        try { cm.registerDefaultNetworkCallback(netCallback, main) } catch (e: Exception) { }
    }

    fun release() {
        main.removeCallbacks(netReconnect)
        player.removeListener(playerListener)
        try { cm.unregisterNetworkCallback(netCallback) } catch (e: Exception) { }
    }

    companion object { const val RESUME_FRESH_MS = 20_000L }
}

/**
 * Saját audio focus kezelés.
 *
 * Az ExoPlayer beépített kezelése minden „átmeneti” focus-vesztésnél (AUDIOFOCUS_LOSS_TRANSIENT)
 * megállítja a lejátszást — ezt kérik sokan értesítési hanghoz, és a rendszer némított/rezgő
 * módban is kiosztja nekik a focust, pedig hang nem is szól. Ezért akadt meg a rádió egy
 * pillanatra némítva is.
 *
 * Itt:
 *  - hívás / csörgés alatt (AudioManager.mode != NORMAL) → szünet, utána automatikusan folytatja,
 *  - más átmeneti hang (értesítés, navigáció, asszisztens) → csak lehalkít,
 *    némított/rezgő módban még azt sem csinálja,
 *  - végleges vesztés (másik zenelejátszó indult) → szünet, mint eddig.
 */
@OptIn(UnstableApi::class)
private class RadioFocus(context: Context, private val player: ExoPlayer) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var hasFocus = false
    private var pausedByFocus = false
    private var ducked = false

    // Átmeneti focus-vesztés alatt figyeljük, nem lett-e belőle hívás: a hívás módja (mode)
    // néha csak a focus-vesztés után áll át, némított módban pedig a felvételkor.
    private val callWatch = object : Runnable {
        override fun run() {
            if (hasFocus) return
            if (inCall() && player.playWhenReady) {
                unduck(); pausedByFocus = true; player.pause()
            } else if (!pausedByFocus) main.postDelayed(this, 500)
        }
    }

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                main.removeCallbacks(callWatch)
                unduck()
                if (pausedByFocus) { pausedByFocus = false; player.play() }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // másik lejátszó vette át: álljunk meg, és ne induljunk vissza magunktól
                hasFocus = false
                pausedByFocus = false
                main.removeCallbacks(callWatch)
                unduck()
                player.pause()
                abandon()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                when {
                    inCall() -> {
                        if (player.playWhenReady) { pausedByFocus = true; player.pause() }
                    }
                    silentRinger() -> Unit          // néma/rezgő: értesítés úgysem szól, nem nyúlunk hozzá
                    else -> duck()
                }
                main.removeCallbacks(callWatch)
                if (!pausedByFocus) main.postDelayed(callWatch, 300)
            }
        }
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        // a halkítást mi csináljuk (és némított módban kihagyjuk), ne a rendszer döntsön helyettünk
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(listener, main)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                pausedByFocus = false
                if (!hasFocus) {
                    val r = am.requestAudioFocus(request)
                    if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) hasFocus = true
                    else player.pause()         // pl. épp hívás van
                }
            } else if (!pausedByFocus) {
                // a felhasználó állította meg: engedjük el a focust
                main.removeCallbacks(callWatch)
                unduck()
                abandon()
            }
        }
    }

    init {
        player.addListener(playerListener)
        if (player.playWhenReady) playerListener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    }

    private fun inCall() = am.mode != AudioManager.MODE_NORMAL
    private fun silentRinger() = am.ringerMode != AudioManager.RINGER_MODE_NORMAL

    private fun duck() { if (!ducked) { ducked = true; player.volume = DUCK_VOLUME } }
    private fun unduck() { if (ducked) { ducked = false; player.volume = 1f } }

    private fun abandon() {
        am.abandonAudioFocusRequest(request)
        hasFocus = false
    }

    fun release() {
        main.removeCallbacks(callWatch)
        player.removeListener(playerListener)
        unduck()
        abandon()
        pausedByFocus = false
    }

    companion object { const val DUCK_VOLUME = 0.25f }
}
