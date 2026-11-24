package hu.lextronic.radio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import hu.lextronic.radio.MainViewModel
import hu.lextronic.radio.Station
import kotlinx.coroutines.delay

/**
 * Teljes képernyős „Most szól” lejátszó: felül tömören a lejátszás,
 * alul a legutóbb játszottak kis keresővel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayer(vm: MainViewModel) {
    val s = vm.playing ?: return
    val (icyArtist, icyTitle) = vm.songParts
    val local = s.isLocal
    var showEq by remember { mutableStateOf(false) }
    if (showEq) EqDialog(vm) { showEq = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // halvány, elmosott borító / logó a háttérben
        if (s.favicon.startsWith("http") || s.favicon.startsWith("content")) {
            AsyncImage(
                model = s.favicon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.28f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black))
            )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            // fejléc: lefelé húzva is bezárható
            Row(
                Modifier.fillMaxWidth()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 25) vm.playerOpen = false } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.playerOpen = false }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bezárás", modifier = Modifier.size(32.dp))
                }
                Text(
                    "MOST SZÓL", style = MaterialTheme.typography.labelLarge, color = Amber,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showEq = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Hangszín", tint = if (vm.eqOn) Amber else Color.White)
                }
                SendToRadioButton(vm, s)
            }

            // logó + név + címkék egy sorban (tömören)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BigLogo(s.favicon, 92)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    val sub = if (local) s.subtitle() else s.tags.split(',').firstOrNull()?.trim().orEmpty()
                    if (sub.isNotBlank()) Text(
                        sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (local) {
                            InfoChip("HELYI")
                            if (vm.queueSize > 1) InfoChip("${vm.queuePos + 1}/${vm.queueSize}")
                        } else {
                            if (s.codec.isNotBlank()) InfoChip(s.codec.uppercase())
                            if (s.bitrate > 0) InfoChip("${s.bitrate} kbps")
                            if (s.country.isNotBlank()) InfoChip(s.country)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Spectrum(vm.isPlaying, height = 64)
            Spacer(Modifier.height(10.dp))

            // a rádió kijelzőjének stílusában
            OledPanel {
                val state = when {
                    vm.buffering -> "PUFFERELÉS…"
                    vm.isPlaying -> if (local) "▶ LEJÁTSZÁS" else "▶ SZÓL  ${s.codec.uppercase()} ${if (s.bitrate > 0) "${s.bitrate}k" else ""}"
                    else -> "❚❚ SZÜNET"
                }
                OledLine(state, bright = false, size = 10)
                if (local) {
                    OledLine(s.name, bright = true, size = 18, bold = true)
                    OledLine(listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { " " }, bright = false, size = 12)
                } else {
                    OledLine(icyTitle.ifBlank { s.name }, bright = true, size = 18, bold = true)
                    OledLine(icyArtist.ifBlank { if (icyTitle.isBlank()) "nincs számcím" else s.name }, bright = icyArtist.isNotBlank(), size = 12)
                }
                OledBar(if (vm.isPlaying) 15 else if (vm.buffering) 7 else 0, 15)
            }

            if (local) SeekBar(vm)

            // vezérlők
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (local) {
                    IconButton(onClick = { vm.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, "Keverés", tint = if (vm.shuffle) Amber else Color.White.copy(alpha = 0.6f))
                    }
                } else {
                    val fav = vm.isFav(s)
                    IconButton(onClick = { vm.toggleFav(s) }) {
                        Icon(
                            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Kedvenc",
                            tint = if (fav) Amber else Color.White, modifier = Modifier.size(26.dp)
                        )
                    }
                }
                IconButton(onClick = { vm.skip(-1) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = if (local) "Előző szám" else "Előző kedvenc", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { vm.togglePause() },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Amber, contentColor = Color.Black)
                ) {
                    if (vm.buffering) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                    else Icon(
                        if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (vm.isPlaying) "Szünet" else "Lejátszás",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { vm.skip(+1) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = if (local) "Következő szám" else "Következő kedvenc", modifier = Modifier.size(34.dp))
                }
                if (local) {
                    IconButton(onClick = { vm.cycleRepeat() }) {
                        Icon(
                            if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Ismétlés",
                            tint = if (vm.repeatMode != Player.REPEAT_MODE_OFF) Amber else Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    IconButton(onClick = { vm.stopPhone() }) { Icon(Icons.Filled.Stop, "Leállítás", modifier = Modifier.size(26.dp)) }
                }
            }

            RecentPanel(vm, Modifier.weight(1f))
        }
    }
}

/** Pozíciócsúszka helyi számokhoz: húzás közben nem ugrál, elengedéskor teker. */
@Composable
private fun SeekBar(vm: MainViewModel) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(vm.playing?.url) {
        while (true) { pos = vm.position(); dur = vm.duration(); delay(250) }
    }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Slider(
            value = frac,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { vm.seekTo((it * dur).toLong()) }; drag = null },
            enabled = dur > 0,
            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
            modifier = Modifier.height(28.dp)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime((frac * dur).toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(dur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * „Legutóbbi” panel a képernyő alján: rádiózás közben a legutóbbi adók, helyi zenénél a legutóbbi
 * zenék (külön listák). A nagyítóval kereső nyílik, szintén csak az éppen használt fajtában.
 */
@Composable
private fun RecentPanel(vm: MainViewModel, modifier: Modifier) {
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val list: List<Station> = if (searching) vm.quickSearch(q) else vm.recentList
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0E0D09))
            .border(1.dp, Color(0xFF2E2A1E), shape)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                BasicTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    cursorBrush = SolidColor(Amber),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text(
                            if (vm.isLocal) "Keresés a zenék között…" else "Keresés: adók, kedvencek…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        inner()
                    }
                )
                IconButton(onClick = { searching = false; q = ""; focus.clearFocus() }) { Icon(Icons.Filled.Close, "Bezárás") }
            } else {
                Text(
                    if (vm.isLocal) "LEGUTÓBBI ZENÉK" else "LEGUTÓBBI ADÓK", style = MaterialTheme.typography.labelMedium, color = Amber.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Keresés") }
            }
        }
        if (list.isEmpty()) {
            Text(
                if (searching) "Nincs találat" else "Még üres — amit meghallgatsz, itt megjelenik.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(list, key = { _, t -> t.url }) { i, t ->
                    val active = vm.playing?.url == t.url
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                focus.clearFocus()
                                // a legutóbbi zenék közül választva maga a lista lesz a lejátszási sor
                                if (t.isLocal && !searching) vm.playLocal(list.toList(), i) else vm.playItem(t)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StationLogo(t.favicon, 34)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) Amber else Color.White,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                            val genre = t.tags.split(',').firstOrNull()?.trim().orEmpty()
                            Text(
                                if (t.isLocal) t.subtitle().ifBlank { "Helyi zene" }
                                else if (genre.isNotEmpty()) "Netrádió · $genre" else "Netrádió",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (t.isLocal) Text(
                            fmtTime(t.durationMs), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
