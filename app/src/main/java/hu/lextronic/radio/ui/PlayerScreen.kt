package hu.lextronic.radio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hu.lextronic.radio.MainViewModel

/** Teljes képernyős lejátszó a telefonos hallgatáshoz (a mini-sávból nyílik). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayer(vm: MainViewModel) {
    val s = vm.playing ?: return
    val (artist, title) = vm.songParts
    val fav = vm.isFav(s)
    var showEq by remember { mutableStateOf(false) }
    if (showEq) EqDialog(vm) { showEq = false }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            // lefelé húzással is bezárható
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy -> if (dy > 30) vm.playerOpen = false }
            }
    ) {
        // halvány, elmosott logó a háttérben (Android 12+ -on mos el, régebbin csak halvány)
        if (s.favicon.startsWith("http")) {
            AsyncImage(
                model = s.favicon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.30f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black))
            )
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                if (vm.radioOnline) SendToRadioButton(vm, s)
            }

            Spacer(Modifier.weight(0.4f))
            BigLogo(s.favicon, 170)
            Spacer(Modifier.height(14.dp))
            Spectrum(vm.isPlaying)
            Spacer(Modifier.height(14.dp))

            Text(
                s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.codec.isNotBlank()) InfoChip(s.codec.uppercase())
                if (s.bitrate > 0) InfoChip("${s.bitrate} kbps")
                if (s.country.isNotBlank()) InfoChip(s.country)
            }

            Spacer(Modifier.height(18.dp))
            // a rádió kijelzőjének stílusában: állapot, előadó, cím
            OledPanel {
                val state = when {
                    vm.buffering -> "PUFFERELÉS…"
                    vm.isPlaying -> "▶ SZÓL  ${s.codec.uppercase()} ${if (s.bitrate > 0) "${s.bitrate}k" else ""}"
                    else -> "❚❚ MEGÁLLÍTVA"
                }
                OledLine(state, bright = false, size = 10)
                OledLine(title.ifBlank { s.name }, bright = true, size = 18, bold = true)
                OledLine(artist.ifBlank { if (title.isBlank()) "nincs számcím" else s.name }, bright = artist.isNotBlank(), size = 12)
                OledBar(if (vm.isPlaying) 15 else if (vm.buffering) 7 else 0, 15)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleFav(s) }) {
                    Icon(
                        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Kedvenc", tint = if (fav) Amber else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { vm.playAdjacent(-1) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Előző kedvenc", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { vm.togglePause() },
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Amber, contentColor = Color.Black)
                ) {
                    if (vm.buffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
                    else Icon(
                        if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (vm.isPlaying) "Szünet" else "Lejátszás",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { vm.playAdjacent(+1) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Következő kedvenc", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.stopPhone() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Leállítás", modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
