package hu.lextronic.radio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.lextronic.radio.MainViewModel
import hu.lextronic.radio.Updater

/** A bal oldalról beúszó menü: verzió, frissítés, kedvencek mentése, megosztás. */
@Composable
fun AppDrawer(vm: MainViewModel, close: () -> Unit) {
    val ctx = LocalContext.current
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Lextronic Rádió", Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge, color = Amber
            )
            Text(
                "Verzió ${vm.versionName}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Item(Icons.Filled.SystemUpdate, "Frissítés keresése", busy = vm.checkingUpdate) {
                close(); vm.checkUpdateManual()
            }
            Item(Icons.Filled.Link, "Letöltési link megosztása") {
                close(); shareText(ctx, "Lextronic Rádió letöltése", Updater.RELEASE_URL)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Kedvencek")
            Item(Icons.Filled.Share, "Mentés / megosztás…") { close(); shareFavs(ctx, vm) }
            Item(Icons.Filled.ContentCopy, "Másolás vágólapra") { close(); copyFavs(ctx, vm) }
            Item(Icons.Filled.ContentPaste, "Importálás vágólapról") { close(); vm.importFavs(clipText(ctx)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Item(Icons.Filled.Code, "Projekt a GitHubon") {
                close()
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Item(icon: ImageVector, label: String, busy: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        badge = { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

private fun shareText(ctx: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, subject))
}
