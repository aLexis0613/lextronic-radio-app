package hu.lextronic.radio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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

/**
 * Az SSD1322 (256×64) kijelzőt idéző képernyő: 4:1 arány, fekete alap,
 * borostyán betűk és halvány képpontrács.
 */
@Composable
fun OledPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF16150F))
            .border(1.dp, Color(0xFF2E2A1E), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .aspectRatio(4f)
                .clip(shape)
                .background(Color.Black)
                .drawWithContent {
                    drawContent()
                    // képpontrács: 64 sor, 256 oszlop – a pontok közti sötét rés adja az OLED-hatást
                    val step = size.height / 64f
                    val gap = Color.Black.copy(alpha = 0.45f)
                    var y = 0f
                    while (y < size.height) { drawLine(gap, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    var x = 0f
                    while (x < size.width) { drawLine(gap, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

/** Egy sor a kijelzőn; ha nem fér ki, lassan fut, mint a rádión. level: 0..3 fényerő (az SSD1322 szürkeárnyalatai). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OledLine(text: String, bright: Boolean = true, size: Int = 15, bold: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        lineHeight = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (bright) OledAmber else OledAmber.copy(alpha = 0.5f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
    )
}

/** Szegmenses csík a kijelző aljára (hangerő / állapot). */
@Composable
fun OledBar(value: Int, max: Int) {
    Row(Modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..max) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i <= value) OledAmber else OledAmber.copy(alpha = 0.12f)))
        }
    }
}

/** Kis címke-chip (AAC, 128 kbps, WiFi …), mint a weblejátszón. */
@Composable
fun InfoChip(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.border(1.dp, Color(0xFF3A3628), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** Nagy, lapos vezérlőgomb (◀ ▶ − +), a weblejátszó gombjai mintájára. */
@Composable
fun PadButton(modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16150F))
            .border(1.dp, Color(0xFF2E2A1E), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
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
