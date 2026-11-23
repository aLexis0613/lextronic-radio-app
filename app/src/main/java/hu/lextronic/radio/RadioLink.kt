package hu.lextronic.radio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Kapcsolat a Lextronic rádió webes API-jával (ugyanaz, amit a weboldala is használ). */
class RadioLink {

    private fun base(host: String) = "http://$host"

    fun state(host: String): RadioState? {
        val t = httpGet(base(host) + "/api/state", 2500) ?: return null
        return parseState(t)
    }

    fun stations(host: String): List<RadioStation>? {
        val t = httpGet(base(host) + "/api/stations", 6000) ?: return null
        return try {
            val a = JSONArray(t)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                RadioStation(i, o.optString("n"), o.optString("g"), o.optString("u"), o.optBoolean("t"))
            }
        } catch (e: Exception) { null }
    }

    /** next | prev | play i | vol i | mute ... — a válasz az új állapot. */
    fun cmd(host: String, c: String, i: Int? = null): RadioState? {
        val url = base(host) + "/api/cmd?c=" + enc(c) + (if (i != null) "&i=$i" else "")
        return httpGet(url, 3000)?.let { parseState(it) }
    }

    /** Állomás mentése a rádió listájába. */
    fun add(host: String, s: Station): Pair<Int, String> =
        httpPost(base(host) + "/api/st/add", mapOf("n" to s.name, "u" to s.url, "g" to s.genre()))

    /** Meghallgatás a rádión mentés nélkül. */
    fun preview(host: String, s: Station): Pair<Int, String> =
        httpPost(base(host) + "/api/st/preview", mapOf("n" to s.name, "u" to s.url, "g" to s.genre()))

    private fun parseState(t: String): RadioState? = try {
        val o = JSONObject(t)
        RadioState(
            idx = o.optInt("idx"),
            count = o.optInt("n"),
            name = o.optString("name"),
            genre = o.optString("genre"),
            title = o.optString("title"),
            status = o.optString("status"),
            codec = o.optString("codec"),
            kbps = o.optInt("kbps"),
            vol = o.optInt("vol"),
            muted = o.optBoolean("muted"),
            pending = o.optBoolean("pending"),
            listVer = o.optInt("lv"),
        )
    } catch (e: Exception) { null }

    /**
     * A rádió megkeresése: előbb a mentett cím, aztán a lextronic.local,
     * végül mDNS-szolgáltatáskeresés (ez minden Androidon megy).
     */
    suspend fun discover(ctx: Context, saved: String?): String? {
        for (h in listOfNotNull(saved, "lextronic.local").distinct()) {
            if (state(h) != null) return h
        }
        val ip = nsdFind(ctx, 6000) ?: return null
        return if (state(ip) != null) ip else null
    }

    private suspend fun nsdFind(ctx: Context, timeoutMs: Long): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val done = AtomicBoolean(false)
            val resolving = AtomicBoolean(false)
            lateinit var disc: NsdManager.DiscoveryListener

            fun finish(r: String?) {
                if (!done.compareAndSet(false, true)) return
                try { nsd.stopServiceDiscovery(disc) } catch (e: Exception) { }
                if (cont.isActive) cont.resume(r)
            }

            disc = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    if (done.compareAndSet(false, true) && cont.isActive) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceName.lowercase().startsWith("lextronic")) return
                    if (!resolving.compareAndSet(false, true)) return
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) { resolving.set(false) }
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val a = si.host
                            val h = when {
                                a == null -> null
                                a is Inet6Address -> "[" + a.hostAddress?.substringBefore('%') + "]"
                                else -> a.hostAddress
                            }
                            finish(h)
                        }
                    })
                }
            }
            try {
                nsd.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, disc)
            } catch (e: Exception) {
                if (done.compareAndSet(false, true) && cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation {
                if (done.compareAndSet(false, true)) {
                    try { nsd.stopServiceDiscovery(disc) } catch (e: Exception) { }
                }
            }
        }
    }
}
