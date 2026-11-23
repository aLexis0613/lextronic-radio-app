package hu.lextronic.radio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hu.lextronic.radio.AudioFx
import hu.lextronic.radio.MainViewModel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Spektrum-kijelző a weblejátszó mintájára: borostyán oszlopok, fölöttük lassan eső csúcsjelek.
 * Csak addig számolunk FFT-t, amíg ez látszik.
 */
@Composable
fun Spectrum(playing: Boolean, modifier: Modifier = Modifier) {
    val n = AudioFx.BANDS
    val bars = remember { FloatArray(n) }
    val peaks = remember { FloatArray(n) }
    val peakHold = remember { IntArray(n) }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)

    DisposableEffect(Unit) {
        AudioFx.wantSpectrum = true
        onDispose { AudioFx.wantSpectrum = false }
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                // ha 400 ms óta nem jött friss adat (szünet, pufferelés), lassan leesik
                val fresh = isPlaying && now - AudioFx.spectrumTime < 400_000_000L
                for (i in 0 until n) {
                    val target = if (fresh) AudioFx.spectrum[i] else 0f
                    bars[i] = if (target > bars[i]) bars[i] + (target - bars[i]) * 0.6f else max(0f, bars[i] - 0.025f)
                    if (bars[i] >= peaks[i]) { peaks[i] = bars[i]; peakHold[i] = 24 }
                    else if (peakHold[i] > 0) peakHold[i]--
                    else peaks[i] = max(0f, peaks[i] - 0.012f)
                }
                frame = now
            }
        }
    }

    val top = Color(0xFFFFDE7A)
    val bottom = Color(0xFF6E5A26)
    Canvas(modifier.fillMaxWidth().height(90.dp)) {
        if (frame < 0L) return@Canvas      // a képkocka-számláló olvasása miatt rajzol újra minden képkockánál
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val h = size.height
        for (i in 0 until n) {
            val x = i * (w + gap)
            val bh = max(2f, bars[i] * (h - 6f))
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(top, bottom), startY = h - bh, endY = h),
                topLeft = Offset(x, h - bh), size = Size(w, bh), cornerRadius = CornerRadius(2f, 2f)
            )
            if (peaks[i] > 0.02f) {
                val py = h - peaks[i] * (h - 6f) - 4f
                drawRect(Color(0xFFFFF1C4), topLeft = Offset(x, py), size = Size(w, 2.5f))
            }
        }
    }
}

private val PRESETS = listOf(
    "Lineáris" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
    "Basszus" to floatArrayOf(7f, 4f, 0f, 0f, 1f),
    "Pop" to floatArrayOf(2f, 3f, 1f, 2f, 3f),
    "Rock" to floatArrayOf(5f, 2f, -2f, 3f, 5f),
    "Beszéd" to floatArrayOf(-4f, 0f, 3f, 4f, 0f),
    "Halk hallgatás" to floatArrayOf(6f, 2f, 0f, 1f, 4f),
)

/** Hangszínszabályzó: 5 sáv ±12 dB, előbeállításokkal. */
@Composable
fun EqDialog(vm: MainViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Kész") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hangszín", modifier = Modifier.weight(1f))
                Switch(checked = vm.eqOn, onCheckedChange = { vm.setEqEnabled(it) })
            }
        },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PRESETS) { (name, g) ->
                        val sel = vm.eqOn == g.any { it != 0f } && (0 until 5).all { vm.eqGains[it] == g[it] }
                        FilterChip(selected = sel, onClick = { vm.eqPreset(g) }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (i in 0 until 5) {
                    val v = vm.eqGains[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(AudioFx.LABELS[i], style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                        Slider(
                            value = v, onValueChange = { vm.setEqBand(i, (it * 2).roundToInt() / 2f) },
                            valueRange = -12f..12f, enabled = true, modifier = Modifier.weight(1f)
                        )
                        Text(
                            (if (v > 0) "+" else "") + (if (v % 1f == 0f) v.toInt().toString() else v.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(36.dp).padding(start = 6.dp)
                        )
                    }
                }
                Text(
                    "A telefonos lejátszásra hat. Emelésnél automatikusan halkít egy kicsit, hogy ne torzuljon.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
