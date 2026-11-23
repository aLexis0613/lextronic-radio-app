package hu.lextronic.radio

import org.json.JSONArray
import java.net.InetAddress
import java.text.Collator
import java.util.Locale

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

    /**
     * Keresés; ha minden üres, a legnépszerűbbeket adja. null = hálózati hiba.
     * Névre keresésnél a pontos egyezés kerül előre (pl. „RES” ne vesszen el az „Express”-ek között),
     * és a legutóbb nem elérhetőnek jelölt állomásokat is mutatjuk (jelölve).
     */
    fun search(name: String, countryCode: String?, tag: String?): List<Station>? {
        val byName = name.isNotBlank()
        val sb = StringBuilder("/json/stations/search?order=clickcount&reverse=true")
        sb.append("&limit=").append(if (byName) 400 else 150)
        sb.append("&hidebroken=").append(!byName)
        if (byName) sb.append("&name=").append(enc(name.trim()))
        if (!countryCode.isNullOrBlank()) sb.append("&countrycode=").append(countryCode)
        if (!tag.isNullOrBlank()) sb.append("&tag=").append(enc(tag))
        val txt = query(sb.toString()) ?: return null
        val list = try {
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
                    ok = o.optInt("lastcheckok", 1) == 1,
                )
            }
        } catch (e: Exception) { return null }
        if (!byName) return list
        // rangsor: pontos név > szóegyezés > eleje egyezik > tartalmazza; azon belül marad a népszerűség
        val q = norm(name)
        fun rank(s: Station): Int {
            val n = norm(s.name)
            val words = s.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            return when {
                n == q -> 0
                words.any { it == q.lowercase() } -> 1
                n.startsWith(q) -> 2
                else -> 3
            } * 2 + (if (s.ok) 0 else 1)
        }
        return list.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }.take(150)
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Országok listája magyar névvel, ábécérendben. */
    fun countries(): List<Country>? {
        val txt = query("/json/countries?hidebroken=true") ?: return null
        return try {
            val hu = Locale("hu", "HU")
            val a = JSONArray(txt)
            val coll = Collator.getInstance(hu)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val code = o.optString("iso_3166_1").uppercase()
                val cnt = o.optInt("stationcount")
                if (code.length != 2 || cnt <= 0) null
                else Country(code, Locale("", code).getDisplayCountry(hu).ifBlank { o.optString("name") }, cnt)
            }.groupBy { it.code }.map { (_, v) -> v.first().copy(count = v.sumOf { it.count }) }
                .sortedWith { x, y -> coll.compare(x.name, y.name) }
        } catch (e: Exception) { null }
    }

    fun countryName(code: String): String =
        Locale("", code).getDisplayCountry(Locale("hu", "HU")).ifBlank { code }

    /** A radio-browser kéri, hogy jelezzük a lejátszást (ebből számolja a népszerűséget). */
    fun click(uuid: String) {
        if (uuid.isNotBlank()) query("/json/url/$uuid")
    }
}
