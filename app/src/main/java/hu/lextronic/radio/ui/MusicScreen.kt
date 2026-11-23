package hu.lextronic.radio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hu.lextronic.radio.MainViewModel

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/** A telefonon tárolt zenék: kereshető lista, koppintásra innen indul a lejátszási sor. */
@Composable
fun MusicScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }

    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("Zenék a telefonról", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A telefonon tárolt zenék lejátszásához engedélyezd a hangfájlok elérését.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { ask.launch(AUDIO_PERMISSION) }) { Text("Engedélyezés") }
        }
        return
    }

    val list = vm.filteredLibrary(vm.libraryQuery)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.libraryQuery,
                onValueChange = { vm.libraryQuery = it },
                placeholder = { Text("Szám, előadó, album…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.libraryQuery.isNotEmpty()) IconButton(onClick = { vm.libraryQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Törlés")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f)
            )
            // keverve lejátssza az egész (szűrt) listát
            IconButton(onClick = {
                focus.clearFocus()
                if (list.isNotEmpty()) {
                    vm.playLocal(list, (list.indices).random())
                    if (!vm.shuffle) vm.toggleShuffle()
                }
            }, enabled = list.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Keverés", tint = Amber)
            }
        }
        Text(
            if (vm.libraryLoading) "Zenék beolvasása…" else "${list.size} szám",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp)
        )
        Box(Modifier.fillMaxSize()) {
            when {
                vm.libraryLoading && vm.library.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.libraryLoaded && vm.library.isEmpty() -> Text(
                    "Nem találtam zenét a telefonon.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, t -> t.url }) { i, t ->
                        StationRow(
                            logo = t.favicon,
                            title = t.name,
                            subtitle = t.subtitle(),
                            active = vm.playing?.url == t.url,
                            onClick = { focus.clearFocus(); vm.playLocal(list, i) },
                            trailing = {
                                Text(
                                    fmtTime(t.durationMs), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 245000 -> „4:05” */
fun fmtTime(ms: Long): String {
    if (ms <= 0) return "–:––"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
