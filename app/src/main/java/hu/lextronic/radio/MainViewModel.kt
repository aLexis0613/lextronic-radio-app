package hu.lextronic.radio

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val rb = RadioBrowser()
    private val link = RadioLink()

    // ---------- lejátszás a telefonon ----------
    private var controller: MediaController? = null
    var playing by mutableStateOf<Station?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var nowTitle by mutableStateOf("")
        private set
    private var retries = 0
    /** A teljes képernyős lejátszó nyitva van-e. */
    var playerOpen by mutableStateOf(false)

    /** Az ICY-cím szétbontva: „Előadó - Cím” -> (előadó, cím). */
    val songParts: Pair<String, String>
        get() {
            val t = nowTitle.trim()
            val i = t.indexOf(" - ")
            return if (i > 0) t.substring(0, i).trim() to t.substring(i + 3).trim() else "" to t
        }

    // ---------- helyi zene, sor, előzmények ----------
    private var queue: List<Station> = emptyList()
    val library = mutableStateListOf<Station>()
    var libraryLoading by mutableStateOf(false)
        private set
    var libraryLoaded by mutableStateOf(false)
        private set
    var libraryQuery by mutableStateOf("")
    val history = mutableStateListOf<Station>().apply { addAll(store.loadHistory()) }
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queuePos by mutableStateOf(0)
        private set
    var queueSize by mutableStateOf(0)
        private set

    val isLocal: Boolean get() = playing?.isLocal == true

    // ---------- hangszín ----------
    val eqGains = mutableStateListOf<Float>().apply { addAll(store.eqGains.toList()) }
    var eqOn by mutableStateOf(store.eqOn)
        private set

    private fun applyEq() {
        AudioFx.setGains(eqGains.toFloatArray(), eqOn)
        store.eqGains = eqGains.toFloatArray(); store.eqOn = eqOn
    }

    fun setEqBand(i: Int, db: Float) { eqGains[i] = db; if (!eqOn) eqOn = true; applyEq() }
    fun setEqEnabled(on: Boolean) { eqOn = on; applyEq() }

    fun eqPreset(g: FloatArray) {
        for (i in 0 until 5) eqGains[i] = g[i]
        eqOn = g.any { it != 0f }
        applyEq()
    }

    // ---------- keresés ----------
    var query by mutableStateOf("")
    var country by mutableStateOf(store.country)          // null = minden ország
        private set
    val countries = mutableStateListOf<Country>()
    var countriesLoading by mutableStateOf(false)
        private set
    var tag by mutableStateOf<String?>(null)
    val results = mutableStateListOf<Station>()
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    // ---------- kedvencek ----------
    val favorites = mutableStateListOf<Station>().apply { addAll(store.loadFavs()) }

    // ---------- a Lextronic rádió ----------
    var radioHost by mutableStateOf<String?>(null)
        private set
    var radioState by mutableStateOf<RadioState?>(null)
        private set
    val radioStations = mutableStateListOf<RadioStation>()
    var radioStatus by mutableStateOf("Rádió keresése…")
        private set
    var searchingRadio by mutableStateOf(false)
        private set
    @Volatile var foreground = true
    /** A Rádió fül látszik-e: csak akkor kérdezzük 2 mp-enként, egyébként ritkán. */
    @Volatile var radioTabVisible = false
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { kick() }
        override fun onLost(network: Network) { kick() }
    }
    @Volatile private var kicked = false
    private var lastLv = -1

    // ---------- üzenetsáv ----------
    var toast by mutableStateOf<String?>(null)

    private val listener = object : Player.Listener {
        // minden változásnál a lejátszó teljes állapotát átvesszük (megbízhatóbb, mint az egyes események)
        override fun onEvents(player: Player, events: Player.Events) { syncFrom(player) }
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            if (playing != null && retries < 3) {
                retries++
                viewModelScope.launch {
                    delay(2000L * retries)
                    c.prepare(); c.play()
                }
            } else {
                toast = "Nem sikerült lejátszani: " + (error.errorCodeName.removePrefix("ERROR_CODE_").lowercase())
            }
        }
    }

    private fun syncFrom(p: Player) {
        // helyi lejátszási sornál a lejátszó magától lép tovább: kövessük, melyik szám szól
        val id = p.currentMediaItem?.mediaId
        if (id != null && playing?.let { it.uuid.ifBlank { it.url } } != id) {
            queue.firstOrNull { it.uuid.ifBlank { it.url } == id }?.let { setPlaying(it) }
        }
        queuePos = p.currentMediaItemIndex
        queueSize = p.mediaItemCount
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        isPlaying = p.isPlaying
        buffering = p.playbackState == Player.STATE_BUFFERING && p.playWhenReady
        if (p.playbackState == Player.STATE_READY) retries = 0
        val t = p.mediaMetadata.title?.toString().orEmpty()
        nowTitle = if (t == playing?.name) "" else t
    }

    init {
        AudioFx.setGains(eqGains.toFloatArray(), eqOn)
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                // ha a háttérben már szólt valami, visszaállítjuk a mini-lejátszót
                if (c.mediaItemCount > 0) playing = store.last
                syncFrom(c)
            } catch (e: Exception) { toast = "A lejátszó nem indult el" }
        }, ContextCompat.getMainExecutor(app))

        try { cm.registerDefaultNetworkCallback(netCb) } catch (e: Exception) { }
        radioLoop()
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (foreground) controller?.let { syncFrom(it) }
            }
        }
    }

    /** A rádiót csak WiFi-n (vagy Etherneten) keressük — mobilneten fölösleges és akkut eszik. */
    private fun onLocalNet(): Boolean {
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override fun onCleared() {
        try { cm.unregisterNetworkCallback(netCb) } catch (e: Exception) { }
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }

    // ================= telefonos lejátszás =================

    fun playOnPhone(s: Station) {
        val c = controller ?: run { toast = "A lejátszó még nem állt készen"; return }
        if (s.isLocal) { playLocal(listOf(s), 0); return }
        queue = listOf(s)
        setPlaying(s)
        nowTitle = ""
        retries = 0
        val meta = MediaMetadata.Builder()
            .setTitle(s.name)
            .setArtist(s.name)
            .setStation(s.name)
            .apply { if (s.favicon.startsWith("http")) setArtworkUri(Uri.parse(s.favicon)) }
            .build()
        val item = MediaItem.Builder()
            .setMediaId(s.uuid.ifBlank { s.url })
            .setUri(s.url)
            .setMediaMetadata(meta)
            .build()
        c.shuffleModeEnabled = false
        c.repeatMode = Player.REPEAT_MODE_OFF
        c.setMediaItem(item)
        c.prepare()
        c.play()
        if (s.uuid.isNotBlank()) viewModelScope.launch(Dispatchers.IO) { rb.click(s.uuid) }
    }

    /** Élő adásnál a szünet = leállítás; folytatáskor friss streamet kérünk, nem a régi puffert. */
    fun togglePause() {
        val c = controller ?: return
        if (isLocal) {
            // helyi fájlnál sima szünet: a pozíció megmarad
            if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
        } else if (c.isPlaying || (c.playWhenReady && c.playbackState != Player.STATE_IDLE)) {
            c.pause(); c.stop()
        } else { retries = 0; c.prepare(); c.play() }
        syncFrom(c)
    }

    private fun setPlaying(s: Station) {
        playing = s
        store.last = s
        val i = history.indexOfFirst { it.url == s.url }
        if (i >= 0) history.removeAt(i)
        history.add(0, s)
        while (history.size > 40) history.removeAt(history.size - 1)
        store.saveHistory(history)
    }

    /** Helyi számok lejátszása; a sorba legfeljebb 500 szám kerül a kiválasztott körül. */
    fun playLocal(list: List<Station>, index: Int) {
        val c = controller ?: run { toast = "A lejátszó még nem állt készen"; return }
        if (list.isEmpty()) return
        val from = (index - 250).coerceAtLeast(0)
        val to = (from + 500).coerceAtMost(list.size)
        val win = list.subList(from, to).toList()
        queue = win
        setPlaying(list[index])
        nowTitle = ""
        val items = win.map { t ->
            MediaItem.Builder()
                .setMediaId(t.uuid)
                .setUri(t.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.name)
                        .setArtist(t.artist.ifBlank { null })
                        .setAlbumTitle(t.album.ifBlank { null })
                        .setArtworkUri(Uri.parse(t.favicon))
                        .build()
                ).build()
        }
        c.setMediaItems(items, index - from, 0L)
        c.prepare()
        c.play()
    }

    /** Bármelyik elem lejátszása (előzmény, keresési találat): helyi szám a könyvtárával együtt, vagy adó. */
    fun playItem(s: Station) {
        if (!s.isLocal) { playOnPhone(s); return }
        val i = library.indexOfFirst { it.url == s.url }
        if (i >= 0) playLocal(library, i) else playLocal(listOf(s), 0)
    }

    /** Előző/következő: helyi zenénél szám, rádiónál kedvenc állomás. */
    fun skip(d: Int) {
        val c = controller ?: return
        if (isLocal) { if (d < 0) c.seekToPrevious() else c.seekToNext(); syncFrom(c) }
        else playAdjacent(d)
    }

    fun position(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: (playing?.durationMs ?: 0L)
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; syncFrom(it) } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            syncFrom(it)
        }
    }

    fun loadLibrary(force: Boolean = false) {
        if (libraryLoading || (libraryLoaded && !force)) return
        libraryLoading = true
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { Library.load(app) }
            library.clear(); library.addAll(l)
            libraryLoading = false; libraryLoaded = true
        }
    }

    fun filteredLibrary(q: String): List<Station> {
        val f = q.trim().lowercase()
        if (f.isEmpty()) return library
        return library.filter { it.name.lowercase().contains(f) || it.artist.lowercase().contains(f) || it.album.lowercase().contains(f) }
    }

    /** A „Most szól” alsó paneljének keresője: előzmények, kedvencek és a helyi zenék. */
    fun quickSearch(q: String): List<Station> {
        val f = q.trim().lowercase()
        if (f.isEmpty()) return history
        val seen = HashSet<String>()
        return (history + favorites + library).filter {
            (it.name.lowercase().contains(f) || it.artist.lowercase().contains(f) || it.tags.lowercase().contains(f)) && seen.add(it.url)
        }.take(60)
    }

    fun clearHistory() { history.clear(); store.saveHistory(history) }

    /** Előző/következő kedvenc (körbe). Ha a mostani nincs a kedvencek közt, az elsővel kezd. */
    private fun playAdjacent(d: Int) {
        if (favorites.isEmpty()) { toast = "Nincs kedvenc állomás"; return }
        val i = favorites.indexOfFirst { it.url == playing?.url }
        val j = if (i < 0) 0 else (i + d + favorites.size) % favorites.size
        playOnPhone(favorites[j])
    }

    fun stopPhone() {
        playerOpen = false
        controller?.run { stop(); clearMediaItems() }
        playing = null
        nowTitle = ""
        isPlaying = false
    }

    // ================= keresés =================

    fun search() {
        searchJob?.cancel()
        searching = true
        searchError = null
        val q = query; val cc = country; val t = tag
        searchJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { rb.search(q, cc, t) }
            searching = false
            if (r == null) { searchError = "A rádiókereső nem érhető el. Van internet?"; return@launch }
            results.clear(); results.addAll(r)
            if (r.isEmpty()) searchError = "Nincs találat"
        }
    }

    fun setCountryFilter(code: String?) {
        country = code
        store.country = code
        search()
    }

    fun countryLabel(): String = country?.let { rb.countryName(it) } ?: "Minden ország"

    fun loadCountries() {
        if (countries.isNotEmpty() || countriesLoading) return
        countriesLoading = true
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { rb.countries() }
            countriesLoading = false
            if (l == null) toast = "Az országlista nem tölthető le" else { countries.clear(); countries.addAll(l) }
        }
    }

    // ================= kedvencek =================

    fun isFav(s: Station) = favorites.any { it.url == s.url }

    fun toggleFav(s: Station) {
        val i = favorites.indexOfFirst { it.url == s.url }
        if (i >= 0) { favorites.removeAt(i); toast = "Törölve a kedvencek közül" }
        else { favorites.add(s); toast = "Hozzáadva a kedvencekhez" }
        store.saveFavs(favorites)
    }

    fun moveFav(i: Int, d: Int) {
        val j = i + d
        if (i !in favorites.indices || j !in favorites.indices) return
        val t = favorites[i]; favorites[i] = favorites[j]; favorites[j] = t
        store.saveFavs(favorites)
    }

    fun radioToStation(r: RadioStation) = Station(uuid = "", name = r.name, url = r.url, tags = r.genre)

    // ================= a Lextronic rádió =================

    val radioOnline get() = radioHost != null && radioState != null

    fun kick() { kicked = true }

    fun rescan() { radioHost = null; radioState = null; kick() }

    fun setManualHost(h: String) {
        val v = h.trim().removePrefix("http://").trimEnd('/')
        if (v.isEmpty()) return
        store.host = v
        rescan()
    }

    private suspend fun waitOrKick(ms: Long) {
        var left = ms
        while (left > 0 && !kicked) { delay(250); left -= 250 }
        kicked = false
    }

    private fun radioLoop() = viewModelScope.launch {
        var fails = 0
        while (isActive) {
            if (!foreground) { delay(1000); continue }
            if (!onLocalNet()) {
                radioHost = null; radioState = null; searchingRadio = false
                radioStatus = "Nincs WiFi – a rádió otthon, WiFi-n érhető el"
                waitOrKick(60_000)          // hálózatváltáskor a callback úgyis felébreszt
                continue
            }
            val h = radioHost
            if (h == null) {
                searchingRadio = true
                radioStatus = "Rádió keresése…"
                val f = withContext(Dispatchers.IO) { link.discover(app, store.host) }
                searchingRadio = false
                if (f != null) {
                    radioHost = f; store.host = f; radioStatus = ""; lastLv = -1; fails = 0
                } else {
                    radioStatus = "A rádió nem található ezen a hálózaton"
                    waitOrKick(if (radioTabVisible) 20_000 else 120_000)
                }
                continue
            }
            val st = withContext(Dispatchers.IO) { link.state(h) }
            if (st == null) {
                if (++fails >= 3) { radioState = null; radioHost = null; fails = 0 }
                waitOrKick(1500)
                continue
            }
            fails = 0
            if (st.listVer != lastLv || radioStations.isEmpty()) {
                val l = withContext(Dispatchers.IO) { link.stations(h) }
                if (l != null) { radioStations.clear(); radioStations.addAll(l); lastLv = st.listVer }
            }
            radioState = st
            waitOrKick(if (radioTabVisible) 2000 else 30_000)
        }
    }

    private fun radioCmd(c: String, i: Int? = null) {
        val h = radioHost ?: return
        viewModelScope.launch {
            val st = withContext(Dispatchers.IO) { link.cmd(h, c, i) }
            if (st != null) radioState = st else toast = "A rádió nem válaszol"
        }
    }

    fun radioNext() = radioCmd("next")
    fun radioPrev() = radioCmd("prev")
    fun radioMute() = radioCmd("mute")
    fun radioVol(v: Int) = radioCmd("vol", v.coerceIn(0, 15))
    fun radioPlay(index: Int) = radioCmd("play", index)

    /** Meghallgatás a rádión (nem kerül a listájába). */
    fun previewOnRadio(s: Station) {
        val h = radioHost ?: run { toast = "Nincs kapcsolat a rádióval"; return }
        viewModelScope.launch {
            val (code, msg) = withContext(Dispatchers.IO) { link.preview(h, s) }
            toast = if (code in 200..299) "Szól a rádión: ${s.name}" else "Hiba: $msg"
            kick()
        }
    }

    /** Mentés a rádió állomáslistájába. */
    fun addToRadio(s: Station) {
        val h = radioHost ?: run { toast = "Nincs kapcsolat a rádióval"; return }
        viewModelScope.launch {
            val (code, msg) = withContext(Dispatchers.IO) { link.add(h, s) }
            toast = when (code) {
                in 200..299 -> "Elmentve a rádióra: ${s.name}"
                409 -> "Ez már rajta van a rádió listáján"
                507 -> "A rádió listája tele van"
                else -> "Hiba: $msg"
            }
            kick()
        }
    }
}
