package hu.lextronic.radio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import hu.lextronic.radio.MainViewModel
import hu.lextronic.radio.Station

/** Állomáslogó; ha nincs vagy nem tölthető be, egy rádió-ikon. */
@Composable
fun StationLogo(url: String, size: Int = 44) {
    val fallback = rememberVectorPainter(Icons.Filled.Radio)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.startsWith("http")) {
            AsyncImage(
                model = url, contentDescription = null,
                modifier = Modifier.size(size.dp), contentScale = ContentScale.Crop,
                error = fallback, fallback = fallback
            )
        } else {
            Icon(Icons.Filled.Radio, null, Modifier.size((size * 0.55).toInt().dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Egy sor a listákban: logó, név, alcím, jobbra a gombok. */
@Composable
fun StationRow(
    logo: String,
    title: String,
    subtitle: String,
    active: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(logo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** „Rádióra” gomb: meghallgatás vagy mentés a Lextronic rádión. Csak akkor látszik, ha a rádió elérhető. */
@Composable
fun SendToRadioButton(vm: MainViewModel, s: Station) {
    if (!vm.radioOnline) return
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.Speaker, contentDescription = "Rádióra", tint = MaterialTheme.colorScheme.secondary)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Lejátszás a rádión") }, onClick = { menuOpen = false; vm.previewOnRadio(s) })
            DropdownMenuItem(text = { Text("Mentés a rádió listájába") }, onClick = { menuOpen = false; vm.addToRadio(s) })
        }
    }
}

/** A rádió kijelzőjét idéző panel: fekete alap, borostyán keret. */
@Composable
fun OledPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF050505))
            .border(1.dp, Amber.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

/** Egy sor a panelen; ha nem fér ki, lassan fut, mint a rádión. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OledLine(text: String, bright: Boolean = true, size: Int = 15, bold: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (bright) Amber else Amber.copy(alpha = 0.55f),
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
    )
}

/** Nagy állomáslogó a teljes lejátszóhoz. */
@Composable
fun BigLogo(url: String, size: Int) {
    val fallback = rememberVectorPainter(Icons.Filled.Radio)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.startsWith("http")) {
            AsyncImage(
                model = url, contentDescription = null,
                modifier = Modifier.size(size.dp), contentScale = ContentScale.Fit,
                error = fallback, fallback = fallback
            )
        } else {
            Icon(Icons.Filled.Radio, null, Modifier.size((size / 2).dp), tint = Amber.copy(alpha = 0.8f))
        }
    }
}
