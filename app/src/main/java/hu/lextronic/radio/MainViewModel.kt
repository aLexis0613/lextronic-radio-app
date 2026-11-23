package hu.lextronic.radio

import android.app.Application
import android.content.ComponentName
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

    // ---------- keresés ----------
    var query by mutableStateOf("")
    var hungaryOnly by mutableStateOf(true)
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
    @Volatile private var kicked = false
    private var lastLv = -1

    // ---------- üzenetsáv ----------
    var toast by mutableStateOf<String?>(null)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        override fun onPlaybackStateChanged(s: Int) {
            buffering = s == Player.STATE_BUFFERING
            if (s == Player.STATE_READY) retries = 0
        }
        override fun onMediaMetadataChanged(m: MediaMetadata) {
            // a stream ICY-címe (előadó – szám) felülírja a címet; ha az állomásnév, nem írjuk ki kétszer
            val t = m.title?.toString().orEmpty()
            nowTitle = if (t == playing?.name) "" else t
        }
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

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                // ha a háttérben már szólt valami, visszaállítjuk a mini-lejátszót
                if (c.mediaItemCount > 0) {
                    playing = store.last
                    isPlaying = c.isPlaying
                    val t = c.mediaMetadata.title?.toString().orEmpty()
                    nowTitle = if (t == playing?.name) "" else t
                }
            } catch (e: Exception) { toast = "A lejátszó nem indult el" }
        }, ContextCompat.getMainExecutor(app))

        radioLoop()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }

    // ================= telefonos lejátszás =================

    fun playOnPhone(s: Station) {
        val c = controller ?: run { toast = "A lejátszó még nem állt készen"; return }
        playing = s
        store.last = s
        nowTitle = ""
        retries = 0
        val meta = MediaMetadata.Builder()
            .setTitle(s.name)
            .setArtist(s.name)
            .apply { if (s.favicon.startsWith("http")) setArtworkUri(Uri.parse(s.favicon)) }
            .build()
        val item = MediaItem.Builder()
            .setMediaId(s.uuid.ifBlank { s.url })
            .setUri(s.url)
            .setMediaMetadata(meta)
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        if (s.uuid.isNotBlank()) viewModelScope.launch(Dispatchers.IO) { rb.click(s.uuid) }
    }

    /** Élő adásnál a szünet = leállítás; folytatáskor friss streamet kérünk, nem a régi puffert. */
    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying || c.playWhenReady) c.stop()
        else { retries = 0; c.prepare(); c.play() }
    }

    fun stopPhone() {
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
        val q = query; val cc = if (hungaryOnly) "HU" else null; val t = tag
        searchJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { rb.search(q, cc, t) }
            searching = false
            if (r == null) { searchError = "A rádiókereső nem érhető el. Van internet?"; return@launch }
            results.clear(); results.addAll(r)
            if (r.isEmpty()) searchError = "Nincs találat"
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
                    waitOrKick(20_000)
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
            waitOrKick(2000)
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
