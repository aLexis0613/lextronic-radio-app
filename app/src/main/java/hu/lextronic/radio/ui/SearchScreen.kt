package hu.lextronic.radio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
    var showCountries by remember { mutableStateOf(false) }
    if (showCountries) CountryDialog(vm) { showCountries = false }
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
                    selected = vm.country != null,
                    onClick = { showCountries = true },
                    label = { Text(vm.countryLabel()) },
                    leadingIcon = { Icon(Icons.Filled.Public, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
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

/** Országválasztó: kereshető lista, elöl a „Minden ország” és Magyarország. */
@Composable
private fun CountryDialog(vm: MainViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadCountries() }
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Bezár") } },
        title = { Text("Ország") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    placeholder = { Text("Ország keresése…") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (vm.countriesLoading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val f = filter.trim().lowercase()
                    val list = vm.countries.filter { f.isEmpty() || it.name.lowercase().contains(f) || it.code.lowercase() == f }
                    val hu = vm.countries.firstOrNull { it.code == "HU" }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        if (f.isEmpty()) {
                            item { CountryRow("Minden ország", null, vm.country == null) { vm.setCountryFilter(null); onClose() } }
                            if (hu != null) item {
                                CountryRow(hu.name, hu.count, vm.country == "HU") { vm.setCountryFilter("HU"); onClose() }
                            }
                            item { HorizontalDivider() }
                        }
                        items(list, key = { it.code }) { c ->
                            CountryRow(c.name, c.count, vm.country == c.code) { vm.setCountryFilter(c.code); onClose() }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CountryRow(name: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name, modifier = Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (count != null) Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
