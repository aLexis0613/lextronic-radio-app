package hu.lextronic.radio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.lextronic.radio.MainViewModel

@Composable
fun LextronicApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(if (vm.favorites.isEmpty()) 1 else 2) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(tab) { vm.radioTabVisible = tab == 0; vm.kick() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
    }

    BackHandler(enabled = vm.playerOpen) { vm.playerOpen = false }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column {
                MiniPlayer(vm)
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Radio, null) }, label = { Text("Rádió") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Keresés") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Kedvencek") }
                    )
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.LibraryMusic, null) }, label = { Text("Zene") }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> RadioScreen(vm)
                1 -> SearchScreen(vm)
                2 -> FavScreen(vm, onSearch = { tab = 1 })
                else -> MusicScreen(vm)
            }
        }
    }
    // a teljes lejátszó alulról csúszik fel, mindent eltakar
    AnimatedVisibility(
        visible = vm.playerOpen && vm.playing != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) { FullPlayer(vm) }
    }
}

/** A telefonon szóló állomás, a navigációs sáv fölött. Koppintásra vagy felfelé húzásra nyílik a teljes lejátszó. */
@Composable
fun MiniPlayer(vm: MainViewModel) {
    val s = vm.playing ?: return
    val fav = vm.isFav(s)
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { vm.playerOpen = true }
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -20) vm.playerOpen = true } }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(s.favicon, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            val sub = when {
                vm.buffering -> "Pufferelés…"
                s.isLocal -> s.subtitle().ifBlank { if (vm.isPlaying) "Lejátszás" else "Szünet" }
                vm.nowTitle.isNotBlank() -> vm.nowTitle
                vm.isPlaying -> "Szól a telefonon"
                else -> "Megállítva"
            }
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.nowTitle.isNotBlank() && !vm.buffering) Amber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s.isLocal) {
            IconButton(onClick = { vm.skip(+1) }) { Icon(Icons.Filled.SkipNext, contentDescription = "Következő szám") }
        } else IconButton(onClick = { vm.toggleFav(s) }) {
            Icon(
                if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Kedvenc",
                tint = if (fav) Amber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (vm.buffering) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { vm.togglePause() }) {
                Icon(
                    if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (vm.isPlaying) "Szünet" else "Lejátszás",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = { vm.stopPhone() }) { Icon(Icons.Filled.Close, contentDescription = "Leállítás") }
    }
}
