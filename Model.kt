package com.unwind.launcher

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin

enum class GlassStyle(val label: String, val blurb: String) {
    Liquid("Liquid Glass", "Refraction, fluid motion"),
    Normal("Normal", "Tinted, soft blur"),
    Frost("Frost Glass", "Heavy blur, milky"),
}

enum class IconShape(val label: String) {
    Circle("Circle"), Squircle("Squircle"), Rounded("Rounded"), Hexagon("Hexagon"),
}

enum class IconStyle(val label: String, val blurb: String) {
    Themed("Themed", "Tinted to your wallpaper"),
    Original("Original", "Full-color app icons"),
}

/** How the pinned apps are laid out on Home. */
enum class HomeLayout(val label: String, val blurb: String) {
    Endless("Endless row", "One row, scrolls sideways"),
    Stacked("Stacked rows", "Wraps into rows below"),
}

/** How the All apps page is laid out. */
enum class AppsView(val label: String, val blurb: String) {
    List("All apps", "One grid, A to Z"),
    Folders("Folders", "Sorted by app type, like the iOS App Library"),
}

/** How close to the bottom edge the home row (the dock) sits. [bottom] is the gap in dp. */
enum class DockPos(val label: String, val bottom: Int) {
    Default("Default", 78),
    Lower("Lower", 40),
    Lowest("Lowest", 14),
}

/** The four parts of the day that each get their own greeting. */
enum class DayPart(val label: String, val hours: String) {
    Morning("Morning", "5 AM to 12 PM"),
    Afternoon("Afternoon", "12 PM to 5 PM"),
    Evening("Evening", "5 PM to 9 PM"),
    Night("Night", "9 PM to 5 AM"),
}

fun dayPartFor(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.Morning
    in 12..16 -> DayPart.Afternoon
    in 17..20 -> DayPart.Evening
    else -> DayPart.Night
}

/** The three lines on Home: a small line, a white headline and a line in the wallpaper's accent color. */
data class Greeting(val top: String, val line1: String, val line2: String)

val DEFAULT_GREETINGS: Map<DayPart, Greeting> = mapOf(
    DayPart.Morning to Greeting("Good morning", "Rise and", "shine"),
    DayPart.Afternoon to Greeting("Good afternoon", "Take a", "breather"),
    DayPart.Evening to Greeting("Good evening", "Time to", "unwind"),
    DayPart.Night to Greeting("Good night", "Time to", "unwind"),
)

/** Where the wallpaper comes from. */
enum class WallSource { Stock, Bing, Custom }

enum class Screen { Home, Apps, Edit, Search, Settings, Gallery, Greeting }
enum class Popup { None, Wifi, Inputs, Account }

private val HexShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(cx, cy)
    for (i in 0 until 6) {
        val a = Math.toRadians((60 * i - 90).toDouble())
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun IconShape.toShape(): Shape = when (this) {
    IconShape.Circle -> CircleShape
    IconShape.Squircle -> RoundedCornerShape(percent = 34)
    IconShape.Rounded -> RoundedCornerShape(percent = 20)
    IconShape.Hexagon -> HexShape
}

/** A wallpaper plus the colors the UI borrows from it. luma = how bright the picture is (0 dark .. 1 bright). */
class Wp(
    val key: String,
    val name: String,
    val accent: Color,
    val tint: Color,
    val dark: Color,
    val luma: Float = 0.45f,
)

val WALLPAPERS = listOf(
    Wp("wp_misty", "Misty Valley", Color(0xFFC5E1A5), Color(0xFF3E6B3C), Color(0xFF1E3322)),
    Wp("wp_emerald", "Emerald Lake", Color(0xFFCDEBC0), Color(0xFF3F6B4A), Color(0xFF1C3324)),
    Wp("wp_golden", "Golden Hour", Color(0xFFFFE2A8), Color(0xFF7A5524), Color(0xFF3A2710)),
    Wp("wp_fog", "Morning Fog", Color(0xFFF3F9EE), Color(0xFF55695E), Color(0xFF26342C)),
    Wp("wp_dusk", "Dusk", Color(0xFFE7D5FA), Color(0xFF5A4A7A), Color(0xFF2A2040)),
    Wp("wp_night", "Night Canopy", Color(0xFF9FE3D0), Color(0xFF1D4A47), Color(0xFF0B2220)),
)

data class LauncherSettings(
    val wallpaper: Int = 0,
    val wallSource: WallSource = WallSource.Stock,
    val shape: IconShape = IconShape.Circle,
    val showNames: Boolean = true,
    val iconStyle: IconStyle = IconStyle.Themed,
    val glass: GlassStyle = GlassStyle.Normal,
    val homeLayout: HomeLayout = HomeLayout.Endless,
    val dock: Boolean = false,
    val dockPos: DockPos = DockPos.Default,
    val appsView: AppsView = AppsView.List,
    val greetings: Map<DayPart, Greeting> = DEFAULT_GREETINGS,
)

private inline fun <reified T : Enum<T>> parseEnum(s: String?, def: T): T =
    enumValues<T>().firstOrNull { it.name == s } ?: def

fun loadSettings(ctx: Context): LauncherSettings {
    val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    val d = LauncherSettings()
    return LauncherSettings(
        wallpaper = p.getInt("wallpaper", d.wallpaper),
        wallSource = parseEnum(p.getString("wallSource", null), d.wallSource),
        shape = parseEnum(p.getString("shape", null), d.shape),
        showNames = p.getBoolean("showNames", d.showNames),
        iconStyle = parseEnum(p.getString("iconStyle", null), d.iconStyle),
        glass = parseEnum(p.getString("glass", null), d.glass),
        homeLayout = parseEnum(p.getString("homeLayout", null), d.homeLayout),
        dock = p.getBoolean("dock", d.dock),
        dockPos = parseEnum(p.getString("dockPos", null), d.dockPos),
        appsView = parseEnum(p.getString("appsView", null), d.appsView),
        greetings = DayPart.values().associateWith { part ->
            val parts = p.getString("greet_${part.name}", null)?.split(GREET_SEP)
            if (parts != null && parts.size == 3) Greeting(parts[0], parts[1], parts[2])
            else DEFAULT_GREETINGS.getValue(part)
        },
    )
}

fun saveSettings(ctx: Context, s: LauncherSettings) {
    ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
        .putInt("wallpaper", s.wallpaper)
        .putString("wallSource", s.wallSource.name)
        .putString("shape", s.shape.name)
        .putBoolean("showNames", s.showNames)
        .putString("iconStyle", s.iconStyle.name)
        .putString("glass", s.glass.name)
        .putString("homeLayout", s.homeLayout.name)
        .putBoolean("dock", s.dock)
        .putString("dockPos", s.dockPos.name)
        .putString("appsView", s.appsView.name)
        .also { e ->
            // Only greetings you changed are stored, so untouched ones keep following the defaults
            for (part in DayPart.values()) {
                val g = s.greetings[part] ?: DEFAULT_GREETINGS.getValue(part)
                if (g == DEFAULT_GREETINGS.getValue(part)) e.remove("greet_${part.name}")
                else e.putString("greet_${part.name}", listOf(g.top, g.line1, g.line2).joinToString(GREET_SEP))
            }
        }
        .apply()
}

private const val GREET_SEP = "\u001F"

/** Focus targets used to wire up D-pad navigation between the dock, top bar and the page content. */
class Nav(
    val content: FocusRequester, // the first thing on the current page
    val pill: FocusRequester,    // the top-right bar (search button)
    val rail: FocusRequester,    // the dock button for the current page
)

/** Everything the glass/theme code needs, provided once at the root. */
class UiCtx(
    val settings: LauncherSettings,
    val wp: Wp,
    val backdrops: Backdrops?,
    val size: IntSize,
)

val LocalUi = compositionLocalOf<UiCtx> { error("UiCtx not provided") }
