package hu.lextronic.radio.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import hu.lextronic.radio.AudioFx
import hu.lextronic.radio.MainActivity
import hu.lextronic.radio.R

// az app színei (AMOLED fekete + borostyán)
private val Black = Color(0xFF000000)
private val Amber = Color(0xFFFFC93C)
private val OledAmber = Color(0xFFFFD54A)
private val OledDim = Color(0xFF6E5A1C)
private val OledBg = Color(0xFF0A0800)
private val TextMain = Color(0xFFF2F2F2)
private val TextDim = Color(0xFFBBBBBB)

private fun cp(c: Color) = ColorProvider(c)

/** A widget állapota a Glance Preferences-ből (LxWidgets írja). */
private class WState(p: Preferences) {
    val line1 = p[W.LINE1] ?: "Lextronic Rádió"
    val line2 = p[W.LINE2] ?: ""
    val info = p[W.INFO] ?: ""
    val playing = p[W.PLAYING] ?: false
    val fav = p[W.FAV] ?: false
    val canFav = p[W.CAN_FAV] ?: false
    val eq = (p[W.EQ] ?: "").split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 5 } ?: List(5) { 0f }
    val eqOn = p[W.EQ_ON] ?: false
    val logoVer = p[W.LOGO_VER] ?: 0L
}

@Composable
private fun logoBitmap(ver: Long): Bitmap? {
    val ctx = LocalContext.current
    return remember(ver) {
        if (ver == 0L) null else try { BitmapFactory.decodeFile(LxWidgets.logoFile(ctx).path) } catch (e: Exception) { null }
    }
}

@Composable
private fun IconBtn(res: Int, desc: String, tint: Color, onClick: androidx.glance.action.Action, size: Int = 36) {
    Box(
        GlanceModifier.size(size.dp).clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(ImageProvider(res), desc, GlanceModifier.size((size * 0.62f).dp), colorFilter = ColorFilter.tint(cp(tint)))
    }
}

@Composable
private fun PlayBtn(playing: Boolean, size: Int) {
    Box(
        GlanceModifier.size(size.dp).background(Amber).cornerRadius((size / 2).dp)
            .clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center
    ) {
        Image(
            ImageProvider(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            if (playing) "Szünet" else "Lejátszás",
            GlanceModifier.size((size * 0.55f).dp), colorFilter = ColorFilter.tint(cp(Black))
        )
    }
}

private fun skip(dir: Int) = actionRunCallback<SkipFavAction>(actionParametersOf(SkipFavAction.DIR to dir))

// ======================= A) kompakt, 4×1 =======================

class CompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Compact(WState(currentState())) }
    }
}

@Composable
private fun Compact(s: WState) {
    Row(
        GlanceModifier.fillMaxSize().background(Black).cornerRadius(20.dp).padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = logoBitmap(s.logoVer)
        Box(
            GlanceModifier.size(42.dp).background(Color(0xFF1C1C1C)).cornerRadius(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) Image(ImageProvider(bmp), null, GlanceModifier.size(42.dp).cornerRadius(10.dp))
            else Image(ImageProvider(R.drawable.ic_w_radio), null, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(cp(Amber)))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(s.line1, maxLines = 1, style = TextStyle(color = cp(TextMain), fontSize = 14.sp, fontWeight = FontWeight.Medium))
            val sub = s.line2.ifBlank { if (s.playing) "Szól" else "Megállítva" }
            Text(sub, maxLines = 1, style = TextStyle(color = cp(if (s.line2.isNotBlank()) Amber else TextDim), fontSize = 12.sp))
        }
        Spacer(GlanceModifier.width(6.dp))
        PlayBtn(s.playing, 40)
        IconBtn(R.drawable.ic_w_next, "Következő kedvenc", TextDim, skip(+1))
    }
}

// ======================= B) OLED panel, 4×2 =======================

class PanelWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Panel(WState(currentState())) }
    }
}

private val mono = TextStyle(color = cp(OledAmber), fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
private fun Panel(s: WState) {
    Column(
        GlanceModifier.fillMaxSize().background(Black).cornerRadius(20.dp).padding(10.dp)
    ) {
        // a „kijelző”: vékony borostyán keret = sötét doboz egy picit világosabb dobozban
        Box(GlanceModifier.fillMaxWidth().defaultWeight().background(Color(0xFF3A2F0A)).cornerRadius(8.dp).padding(1.dp)) {
            Row(
                GlanceModifier.fillMaxSize().background(OledBg).cornerRadius(7.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text("LEXTRONIC", style = mono.copy(color = cp(OledDim), fontSize = 10.sp))
                        Spacer(GlanceModifier.defaultWeight())
                        Text(s.info, style = mono.copy(color = cp(OledDim), fontSize = 10.sp))
                    }
                    Text(s.line1.uppercase(), maxLines = 1, style = mono.copy(fontSize = 15.sp))
                    val sub = if (s.line2.isNotBlank()) "» " + s.line2 else if (s.playing) "» szól" else "■ megállítva"
                    Text(sub, maxLines = 1, style = mono)
                }
                Spacer(GlanceModifier.width(10.dp))
                EqBars(s.eq, s.eqOn)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(GlanceModifier.defaultWeight())
            IconBtn(R.drawable.ic_w_prev, "Előző kedvenc", TextDim, skip(-1), 40)
            Spacer(GlanceModifier.width(14.dp))
            PlayBtn(s.playing, 44)
            Spacer(GlanceModifier.width(14.dp))
            IconBtn(R.drawable.ic_w_next, "Következő kedvenc", TextDim, skip(+1), 40)
            Spacer(GlanceModifier.width(14.dp))
            if (s.canFav) IconBtn(
                if (s.fav) R.drawable.ic_w_star else R.drawable.ic_w_star_border,
                "Kedvenc", Amber, actionRunCallback<ToggleFavAction>(), 40
            ) else Spacer(GlanceModifier.width(40.dp))
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

/** A hangszínszabályzó 5 sávja oszlopokként (-12..+12 dB), alattuk a frekvenciák. */
@Composable
private fun EqBars(eq: List<Float>, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (on) "EQ" else "EQ KI", style = mono.copy(color = cp(OledDim), fontSize = 9.sp))
        Row(GlanceModifier.height(34.dp), verticalAlignment = Alignment.Bottom) {
            eq.forEachIndexed { i, g ->
                if (i > 0) Spacer(GlanceModifier.width(3.dp))
                // 0 dB = középmagasság; kikapcsolva halvány, lapos sor
                val h = if (on) (17 + g / 12f * 15f).coerceIn(2f, 32f) else 3f
                Box(GlanceModifier.width(7.dp).height(h.dp).background(if (on) OledAmber else OledDim)) {}
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row {
            AudioFx.LABELS.forEachIndexed { i, l ->
                if (i > 0) Spacer(GlanceModifier.width(3.dp))
                Box(GlanceModifier.width(7.dp), contentAlignment = Alignment.Center) {
                    Text(l.take(1), style = mono.copy(color = cp(OledDim), fontSize = 7.sp))
                }
            }
        }
    }
}

// ======================= fogadók (manifestben) =======================

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LxWidgets.refresh(context)
    }
}

class PanelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PanelWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LxWidgets.refresh(context)
    }
}
