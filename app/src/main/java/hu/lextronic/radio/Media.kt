package hu.lextronic.radio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Netrádió-állomásból lejátszható MediaItem (az app és a widget is ezt használja). */
fun Station.toRadioItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(name)
        .setArtist(name)
        .setStation(name)
        .apply { if (favicon.startsWith("http")) setArtworkUri(Uri.parse(favicon)) }
        .build()
    return MediaItem.Builder()
        .setMediaId(uuid.ifBlank { url })
        .setUri(url)
        .setMediaMetadata(meta)
        .build()
}
