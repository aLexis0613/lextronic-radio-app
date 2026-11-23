package hu.lextronic.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.lextronic.radio.MainViewModel
import hu.lextronic.radio.RadioState
import hu.lextronic.radio.RadioStation
import kotlin.math.roundToInt

@Composable
fun RadioScreen(vm: MainViewModel) {
    val st = vm.radioState
    if (vm.radioHost == null || st == null) { RadioNotFound(vm); return }

    LazyColumn(Modifier.fillMaxWidth()) {
        item { NowOnRadio(vm, st) }
        item {
            Text(
                "Állomások a rádión (${vm.radioStations.count { !it.temp }})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        items(vm.radioStations, key = { it.index.toString() + it.url }) { r ->
            StationRow(
                logo = "",
                title = r.name,
                subtitle = if (r.temp) "meghallgatás (nincs elmentve)" else r.genre,
                active = r.index == st.idx,
                onClick = { vm.radioPlay(r.index) },
                trailing = { RadioStationMenu(vm, r) }
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RadioStationMenu(vm: MainViewModel, r: RadioStation) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Továbbiak") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Lejátszás a rádión") }, onClick = { menuOpen = false; vm.radioPlay(r.index) })
            DropdownMenuItem(text = { Text("Lejátszás a telefonon") }, onClick = { menuOpen = false; vm.playOnPhone(vm.radioToStation(r)) })
            val s = vm.radioToStation(r)
            DropdownMenuItem(
                text = { Text(if (vm.isFav(s)) "Törlés a kedvencek közül" else "Hozzáadás a kedvencekhez") },
                onClick = { menuOpen = false; vm.toggleFav(s) }
            )
        }
    }
}

@Composable
private fun NowOnRadio(vm: MainViewModel, st: RadioState) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            // a rádió kijelzőjét idéző panel
            OledPanel {
                val pos = if (st.count > 0) "%02d/%02d".format(st.idx + 1, st.count) else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LEXTRONIC  $pos", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = Amber.copy(alpha = 0.55f), modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (st.muted) "NÉMA" else "VOL %02d".format(st.vol), fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp, color = Amber.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OledLine(st.name.ifBlank { "—" }, bright = true, size = 20, bold = true)
                Spacer(Modifier.height(2.dp))
                OledLine(st.title.ifBlank { st.genre.ifBlank { " " } }, bright = st.title.isNotBlank(), size = 15)
                Spacer(Modifier.height(8.dp))
                val info = listOf(st.status, if (st.kbps > 0) "${st.codec} ${st.kbps}k" else st.codec)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                OledLine(info.uppercase().ifBlank { " " }, bright = false, size = 11)
                Spacer(Modifier.height(8.dp))
                // hangerő-csík, mint a kijelzőn
                Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 1..15) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(
                                if (!st.muted && i <= st.vol) Amber else Amber.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val cur = vm.radioStations.getOrNull(st.idx)?.let { vm.radioToStation(it) }
                IconButton(onClick = { cur?.let { vm.toggleFav(it) } }, enabled = cur != null) {
                    val fav = cur != null && vm.isFav(cur)
                    Icon(
                        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Kedvencekhez",
                        tint = if (fav) Amber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { vm.radioPrev() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Előző")
                }
                Spacer(Modifier.width(16.dp))
                FilledIconButton(onClick = { vm.radioMute() }, modifier = Modifier.size(60.dp)) {
                    Icon(if (st.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Némítás")
                }
                Spacer(Modifier.width(16.dp))
                FilledTonalIconButton(onClick = { vm.radioNext() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Következő")
                }
                Spacer(Modifier.width(8.dp))
                // a rádión szóló adó a telefonon is (pl. ha elindulsz otthonról)
                IconButton(onClick = { cur?.let { vm.playOnPhone(it) } }, enabled = cur != null) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = "Lejátszás a telefonon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
            // a csúszkát a rádió felől érkező érték csak akkor írja felül, ha épp nem húzzuk
            var dragV by remember { mutableStateOf<Float?>(null) }
            val v = dragV ?: st.vol.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hangerő", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                Slider(
                    value = v,
                    onValueChange = { dragV = it },
                    onValueChangeFinished = { dragV?.let { vm.radioVol(it.roundToInt()) }; dragV = null },
                    valueRange = 0f..15f, steps = 14,
                    modifier = Modifier.weight(1f)
                )
                Text(if (st.muted) "néma" else v.roundToInt().toString(), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun RadioNotFound(vm: MainViewModel) {
    var ip by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        if (vm.searchingRadio) CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(vm.radioStatus, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Otthon, ugyanazon a WiFi-n magától megtalálja a lextronic.local-t. " +
                "Úton a Keresés és a Kedvencek fül önállóan is működik.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { vm.rescan() }, enabled = !vm.searchingRadio) { Text("Újrakeresés") }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ip, onValueChange = { ip = it },
            label = { Text("Vagy a rádió IP-címe") }, placeholder = { Text("pl. 192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.setManualHost(ip) }, enabled = ip.isNotBlank()) { Text("Csatlakozás") }
    }
}
