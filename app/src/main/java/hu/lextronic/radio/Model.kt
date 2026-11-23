package hu.lextronic.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Egy netrádió-állomás (a radio-browser.info-ból vagy a kedvencek közül). */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val ok: Boolean = true,          // a radio-browser legutóbbi ellenőrzésekor elérhető volt-e
) {
    fun subtitle(): String {
        val p = mutableListOf<String>()
        if (!ok) p += "⚠ lehet, hogy nem elérhető"
        if (country.isNotBlank()) p += country
        if (codec.isNotBlank()) p += if (bitrate > 0) "$codec $bitrate kbps" else codec
        val t = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (t.isNotEmpty()) p += t.joinToString(", ")
        return p.joinToString(" · ")
    }

    /** A rádió listájába kerülő műfaj: az első címke. */
    fun genre(): String = tags.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid).put("name", name).put("url", url).put("favicon", favicon)
        .put("tags", tags).put("country", country).put("codec", codec).put("bitrate", bitrate)

    companion object {
        fun fromJson(o: JSONObject) = Station(
            uuid = o.optString("uuid"),
            name = o.optString("name"),
            url = o.optString("url"),
            favicon = o.optString("favicon"),
            tags = o.optString("tags"),
            country = o.optString("country"),
            codec = o.optString("codec"),
            bitrate = o.optInt("bitrate"),
        )
    }
}

/** Ország a szűrőhöz (kód + magyar név + állomásszám). */
data class Country(val code: String, val name: String, val count: Int)

/** A rádió /api/state válasza (ami nekünk kell belőle). */
data class RadioState(
    val idx: Int,
    val count: Int,
    val name: String,
    val genre: String,
    val title: String,
    val status: String,
    val codec: String,
    val kbps: Int,
    val vol: Int,
    val muted: Boolean,
    val pending: Boolean,
    val listVer: Int,
)

/** Egy állomás a rádió saját listájában. */
data class RadioStation(val index: Int, val name: String, val genre: String, val url: String, val temp: Boolean)

/** Kedvencek, a rádió címe és az utoljára játszott állomás a telefonon. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lextronic", Context.MODE_PRIVATE)

    var host: String?
        get() = sp.getString("host", null)
        set(v) { sp.edit().putString("host", v).apply() }

    fun loadFavs(): List<Station> = try {
        val a = JSONArray(sp.getString("favs", "[]"))
        (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveFavs(list: List<Station>) {
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        sp.edit().putString("favs", a.toString()).apply()
    }

    /** A keresés országa: kétbetűs kód, vagy null = minden ország. */
    var country: String?
        get() = sp.getString("country", "HU").takeIf { it != "ALL" }
        set(v) { sp.edit().putString("country", v ?: "ALL").apply() }

    var eqGains: FloatArray
        get() = sp.getString("eq", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }?.toFloatArray() ?: FloatArray(5)
        set(v) { sp.edit().putString("eq", v.joinToString(",")).apply() }

    var eqOn: Boolean
        get() = sp.getBoolean("eqOn", false)
        set(v) { sp.edit().putBoolean("eqOn", v).apply() }

    var last: Station?
        get() = sp.getString("last", null)?.let { try { Station.fromJson(JSONObject(it)) } catch (e: Exception) { null } }
        set(v) { sp.edit().putString("last", v?.toJson()?.toString()).apply() }
}
