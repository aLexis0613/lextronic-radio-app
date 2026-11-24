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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.graphics.Color
import hu.lextronic.radio.MusicGroup
import hu.lextronic.radio.Station

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * A telefonon tárolt zenék, fülekkel: Számok, Albumok, Előadók, Legutóbbi (max. 50).
 * Amelyik listából választasz, az lesz a lejátszási sor (album → az album, összes szám → mind).
 */
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

    val group = vm.musicGroup
    BackHandler(enabled = group != null) { vm.musicGroup = null }

    Column(Modifier.fillMaxSize()) {
        if (group != null) {
            GroupHeader(vm, group)
            TrackList(vm, group.tracks, numbered = vm.musicTab == 1) { t ->
                if (vm.musicTab == 1) t.artist else t.album
            }
            return@Column
        }

        TabRow(
            selectedTabIndex = vm.musicTab,
            containerColor = Color.Transparent,
            contentColor = Amber,
        ) {
            listOf("Számok", "Albumok", "Előadók", "Legutóbbi").forEachIndexed { i, label ->
                Tab(
                    selected = vm.musicTab == i,
                    onClick = { vm.musicTab = i; focus.clearFocus() },
                    text = { Text(label, maxLines = 1) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val q = vm.libraryQuery.trim().lowercase()
        fun Station.hit() = q.isEmpty() || name.lowercase().contains(q) || artist.lowercase().contains(q) || album.lowercase().contains(q)
        fun MusicGroup.hit() = q.isEmpty() || title.lowercase().contains(q) || subtitle.lowercase().contains(q)

        val tracks: List<Station> = when (vm.musicTab) {
            0 -> vm.filteredLibrary(vm.libraryQuery)
            3 -> vm.musicHistory.filter { it.hit() }
            else -> emptyList()
        }
        val groups: List<MusicGroup> = when (vm.musicTab) {
            1 -> vm.albums.filter { it.hit() }
            2 -> vm.artists.filter { it.hit() }
            else -> emptyList()
        }
        // a keverés gomb mindig azt keveri, ami épp látszik (csoportoknál azok összes számát)
        val shuffleList = if (vm.musicTab == 1 || vm.musicTab == 2) groups.flatMap { it.tracks } else tracks

        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.libraryQuery,
                onValueChange = { vm.libraryQuery = it },
                placeholder = {
                    Text(
                        when (vm.musicTab) { 1 -> "Album keresése…"; 2 -> "Előadó keresése…"; else -> "Szám, előadó, album…" }
                    )
                },
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
            IconButton(onClick = {
                focus.clearFocus()
                if (shuffleList.isNotEmpty()) {
                    vm.playLocal(shuffleList, shuffleList.indices.random())
                    if (!vm.shuffle) vm.toggleShuffle()
                }
            }, enabled = shuffleList.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Keverés", tint = Amber)
            }
        }
        Text(
            when {
                vm.libraryLoading -> "Zenék beolvasása…"
                vm.musicTab == 1 -> "${groups.size} album"
                vm.musicTab == 2 -> "${groups.size} előadó"
                vm.musicTab == 3 -> "${tracks.size} legutóbb játszott szám (max. 50)"
                else -> "${tracks.size} szám"
            },
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
                vm.musicTab == 3 && vm.musicHistory.isEmpty() -> Text(
                    "Még nem hallgattál zenét a telefonról.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                vm.musicTab == 1 || vm.musicTab == 2 -> LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.key }) { g ->
                        StationRow(
                            logo = g.art, title = g.title, subtitle = g.subtitle,
                            onClick = { focus.clearFocus(); vm.musicGroup = g },
                            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                }
                else -> TrackList(vm, tracks)
            }
        }
    }
}

/** Számlista; koppintásra pontosan ez a lista lesz a lejátszási sor. */
@Composable
private fun TrackList(
    vm: MainViewModel,
    list: List<Station>,
    numbered: Boolean = false,
    subtitle: (Station) -> String = { it.subtitle() },
) {
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, t -> t.url }) { i, t ->
            val no = t.track % 1000
            StationRow(
                logo = t.favicon,
                title = if (numbered && no > 0) "$no. ${t.name}" else t.name,
                subtitle = subtitle(t),
                active = vm.playing?.url == t.url,
                onClick = { focus.clearFocus(); vm.playLocal(list.toList(), i) },
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

/** Megnyitott album / előadó fejléce: vissza, borító, cím, Lejátszás és Keverés. */
@Composable
private fun GroupHeader(vm: MainViewModel, g: MusicGroup) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.musicGroup = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
            }
            StationLogo(g.art, 64)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                if (vm.shuffle) vm.toggleShuffle()
                vm.playLocal(g.tracks, 0)
            }) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Lejátszás")
            }
            OutlinedButton(onClick = {
                vm.playLocal(g.tracks, g.tracks.indices.random())
                if (!vm.shuffle) vm.toggleShuffle()
            }) {
                Icon(Icons.Filled.Shuffle, null, tint = Amber); Spacer(Modifier.width(6.dp)); Text("Keverés")
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
