package hu.lextronic.radio

import org.json.JSONArray
import java.net.InetAddress

/** Ingyenes, nyílt netrádió-adatbázis: https://www.radio-browser.info */
class RadioBrowser {
    private var servers: List<String>? = null

    /** A hivatalos módszer: az all.api címeit visszafejtjük szervernevekre. */
    private fun serverList(): List<String> {
        servers?.let { return it }
        val found = try {
            InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { a -> a.canonicalHostName.takeIf { it.endsWith("radio-browser.info") } }
                .distinct().shuffled()
        } catch (e: Exception) { emptyList() }
        val list = (found + listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info"))
            .distinct().map { "https://$it" }
        servers = list
        return list
    }

    private fun query(path: String): String? {
        for (s in serverList()) {
            httpGet(s + path, 8000)?.let { return it }
        }
        servers = null      // legközelebb újra lekérjük a listát
        return null
    }

    /** Keresés; ha minden üres, a legnépszerűbbeket adja. null = hálózati hiba. */
    fun search(name: String, countryCode: String?, tag: String?): List<Station>? {
        val sb = StringBuilder("/json/stations/search?limit=100&hidebroken=true&order=clickcount&reverse=true")
        if (name.isNotBlank()) sb.append("&name=").append(enc(name.trim()))
        if (!countryCode.isNullOrBlank()) sb.append("&countrycode=").append(countryCode)
        if (!tag.isNullOrBlank()) sb.append("&tag=").append(enc(tag))
        val txt = query(sb.toString()) ?: return null
        return try {
            val a = JSONArray(txt)
            val seen = HashSet<String>()
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                val nm = o.optString("name").trim()
                if (url.isBlank() || nm.isBlank() || !seen.add(url)) null
                else Station(
                    uuid = o.optString("stationuuid"),
                    name = nm,
                    url = url,
                    favicon = o.optString("favicon"),
                    tags = o.optString("tags"),
                    country = o.optString("countrycode"),
                    codec = o.optString("codec"),
                    bitrate = o.optInt("bitrate"),
                )
            }
        } catch (e: Exception) { null }
    }

    /** A radio-browser kéri, hogy jelezzük a lejátszást (ebből számolja a népszerűséget). */
    fun click(uuid: String) {
        if (uuid.isNotBlank()) query("/json/url/$uuid")
    }
}
