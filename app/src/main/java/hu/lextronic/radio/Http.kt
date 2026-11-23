package hu.lextronic.radio

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal const val UA = "LextronicRadio/1.0 (Android)"

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Egyszerű GET; hiba vagy nem 2xx válasz esetén null. */
internal fun httpGet(url: String, timeoutMs: Int = 3000): String? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    c.setRequestProperty("User-Agent", UA)
    try {
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
    } finally { c.disconnect() }
} catch (e: Exception) { null }

/** Űrlapos POST; visszaadja a státuszkódot és a választ (hálózati hibánál -1). */
internal fun httpPost(url: String, form: Map<String, String>, timeoutMs: Int = 4000): Pair<Int, String> = try {
    val body = form.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }.toByteArray()
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    c.requestMethod = "POST"
    c.doOutput = true
    c.setRequestProperty("User-Agent", UA)
    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    try {
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        code to s
    } finally { c.disconnect() }
} catch (e: Exception) { -1 to (e.message ?: "hálózati hiba") }
