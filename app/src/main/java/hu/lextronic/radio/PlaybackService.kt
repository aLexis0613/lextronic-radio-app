package hu.lextronic.radio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Háttérlejátszás: a hang akkor is szól, ha az app nincs előtérben vagy a képernyő le van zárva.
 * Értesítés és lezárási képernyő-vezérlők a Media3-tól jönnek.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
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
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sources)
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true                                   // hívásnál, navigációnál elhalkul / megáll
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

        // hardveres (DSP) dekódolás, ha a telefon tudja: a processzor közben alhat -> kevesebb fogyasztás.
        // Ha a telefon vagy a formátum nem támogatja, az ExoPlayer magától a normál dekódolást használja.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .build()
            )
            .build()

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
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
