package hu.lextronic.radio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.lextronic.radio.MainViewModel
import hu.lextronic.radio.Station

@Composable
fun FavScreen(vm: MainViewModel, onSearch: () -> Unit) {
    if (vm.favorites.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("Még nincs kedvenced", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A Keresésben a ☆ gombbal, a Rádió fülön pedig a ⋮ menüből adhatsz hozzá állomást.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSearch) { Text("Állomások keresése") }
            Spacer(Modifier.height(8.dp))
            val ctx = LocalContext.current
            OutlinedButton(onClick = { vm.importFavs(clipText(ctx)) }) { Text("Kedvencek importálása vágólapról") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "favs-header") { FavHeader(vm) }
        itemsIndexed(vm.favorites, key = { _, s -> s.url }) { i, s ->
            StationRow(
                logo = s.favicon,
                title = s.name,
                subtitle = s.subtitle(),
                active = vm.playing?.url == s.url,
                onClick = { vm.playOnPhone(s) },
                trailing = {
                    SendToRadioButton(vm, s)
                    FavMenu(vm, i, s)
                }
            )
        }
    }
}

@Composable
private fun FavMenu(vm: MainViewModel, i: Int, s: Station) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Továbbiak") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (i > 0) DropdownMenuItem(text = { Text("Feljebb") }, onClick = { menuOpen = false; vm.moveFav(i, -1) })
            if (i < vm.favorites.size - 1) DropdownMenuItem(text = { Text("Lejjebb") }, onClick = { menuOpen = false; vm.moveFav(i, +1) })
            DropdownMenuItem(text = { Text("Törlés a kedvencek közül") }, onClick = { menuOpen = false; vm.toggleFav(s) })
        }
    }
}

/** A lista teteje: darabszám és a mentés/visszatöltés menü. */
@Composable
private fun FavHeader(vm: MainViewModel) {
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${vm.favorites.size} kedvenc", Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Kedvencek mentése") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Másolás vágólapra") }, onClick = { menuOpen = false; copyFavs(ctx, vm) })
                DropdownMenuItem(text = { Text("Megosztás / mentés…") }, onClick = { menuOpen = false; shareFavs(ctx, vm) })
                DropdownMenuItem(text = { Text("Importálás vágólapról") }, onClick = {
                    menuOpen = false
                    vm.importFavs(clipText(ctx))
                })
            }
        }
    }
}

internal fun copyFavs(ctx: Context, vm: MainViewModel) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Lextronic kedvencek", vm.exportFavs()))
    vm.toast = "Kedvencek a vágólapon"
}

internal fun shareFavs(ctx: Context, vm: MainViewModel) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Lextronic kedvencek")
        .putExtra(Intent.EXTRA_TEXT, vm.exportFavs())
    ctx.startActivity(Intent.createChooser(send, "Kedvencek mentése"))
}

internal fun clipText(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
}
