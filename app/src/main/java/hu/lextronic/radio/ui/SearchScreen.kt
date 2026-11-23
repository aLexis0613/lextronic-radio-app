package hu.lextronic.radio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import hu.lextronic.radio.MainViewModel

private val TAGS = listOf(
    "pop" to "Pop", "rock" to "Rock", "dance" to "Dance", "news" to "Hírek", "talk" to "Beszélgetős",
    "jazz" to "Jazz", "classical" to "Klasszikus", "retro" to "Retró", "80s" to "80-as évek",
    "electronic" to "Elektronikus", "chillout" to "Chill", "metal" to "Metal", "folk" to "Népzene",
)

@Composable
fun SearchScreen(vm: MainViewModel) {
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { if (vm.results.isEmpty() && !vm.searching) vm.search() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Állomás neve…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = ""; vm.search() }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Törlés")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = vm.hungaryOnly,
                    onClick = { vm.hungaryOnly = !vm.hungaryOnly; vm.search() },
                    label = { Text("Csak magyar") }
                )
            }
            items(TAGS) { (tag, label) ->
                FilterChip(
                    selected = vm.tag == tag,
                    onClick = { vm.tag = if (vm.tag == tag) null else tag; vm.search() },
                    label = { Text(label) }
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                vm.searching && vm.results.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.searchError != null && vm.results.isEmpty() ->
                    Text(
                        vm.searchError ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.results, key = { it.url }) { s ->
                        StationRow(
                            logo = s.favicon,
                            title = s.name,
                            subtitle = s.subtitle(),
                            active = vm.playing?.url == s.url,
                            onClick = { vm.playOnPhone(s) },
                            trailing = {
                                SendToRadioButton(vm, s)
                                IconButton(onClick = { vm.toggleFav(s) }) {
                                    val fav = vm.isFav(s)
                                    Icon(
                                        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Kedvenc",
                                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
            if (vm.searching && vm.results.isNotEmpty())
                CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp))
        }
    }
}
