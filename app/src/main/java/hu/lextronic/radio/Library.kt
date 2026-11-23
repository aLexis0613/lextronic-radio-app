package hu.lextronic.radio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** A telefon zenéi a MediaStore-ból (amit a rendszer zenének ismer fel). */
object Library {
    private val artBase: Uri = Uri.parse("content://media/external/audio/albumart")

    fun load(ctx: Context): List<Station> {
        val out = ArrayList<Station>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000",
                null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val art = ContentUris.withAppendedId(artBase, c.getLong(iAlbumId))
                    out += Station(
                        uuid = "local:$id",
                        name = c.getString(iTitle) ?: "Ismeretlen szám",
                        url = uri.toString(),
                        favicon = art.toString(),
                        artist = c.getString(iArtist).orEmpty().takeIf { it != "<unknown>" }.orEmpty(),
                        album = c.getString(iAlbum).orEmpty(),
                        durationMs = c.getLong(iDur),
                    )
                }
            }
        } catch (e: Exception) { }
        return out
    }
}
