package com.unwind.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AppEntry(
    val pkg: String,
    val label: String,
    val intent: Intent,
    val icon: ImageBitmap,  // full-color icon, pre-scaled to the size it's drawn at
    val mask: ImageBitmap?, // white glyph with alpha, cropped tight; null when no clean glyph could be isolated
    val bg: Color,          // the icon's own background color (Transparent if it has none)
    val folder: String = "Other", // automatic folder, from the app's category
)

/** Icons are expensive to analyze, so each one is built once per app launch of the launcher. */
private class Built(val icon: ImageBitmap, val mask: ImageBitmap?, val bg: Color)

private val iconCache = HashMap<String, Built>()

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val density = ctx.resources.displayMetrics.density
    // Tiles are 76dp; the glyph box is 58% of that and the full icon 62%. Aim a little above for crisp scaling.
    val maskPx = (76f * 0.58f * density * 1.12f).roundToInt().coerceIn(64, WORK)
    val iconPx = (76f * 0.62f * density * 1.12f).roundToInt().coerceIn(64, WORK)
    val out = LinkedHashMap<String, AppEntry>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || out.containsKey(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val key = "$pkg/${ri.activityInfo.name}@$maskPx"
            val built = builtFor(key, { ri.loadIcon(pm) }, maskPx, iconPx)
            out[pkg] = AppEntry(
                pkg, ri.loadLabel(pm).toString(), launch, built.icon, built.mask, built.bg,
                folderFor(ri.activityInfo.applicationInfo, pkg),
            )
        }
    }
    return out.values.sortedBy { it.label.lowercase() }
}

// ---------- Automatic folders ----------

/** The folders, in the order they appear. "Other" catches anything that doesn't fit. */
val FOLDER_ORDER = listOf("Video", "Music", "Games", "Social", "News", "Photos", "Productivity", "Other")

private val VIDEO_HINTS = listOf(
    "netflix", "youtube", "hotstar", "amazonvideo", "primevideo", "jio.media", "atve", "plex", "kodi",
    "disney", "hbo", "sonyliv", "zee5", "mxtech", "vlc", "twitch", "tubi", "hulu", "tv.player", "livingroom",
)
private val MUSIC_HINTS = listOf("music", "spotify", "audio", "podcast", "saavn", "gaana", "wynk", "deezer", "tidal", "radio")
private val SOCIAL_HINTS = listOf("facebook", "instagram", "twitter", "whatsapp", "telegram", "snapchat", "reddit")
private val PHOTO_HINTS = listOf("photos", "gallery", "camera")
private val PRODUCTIVITY_HINTS = listOf("docs", "office", "drive", "sheets", "slides", "calendar", "keep", "notes")

/** Uses the category the app declares. Many TV apps declare none, so common ones are matched by name. */
@Suppress("DEPRECATION")
fun folderFor(ai: android.content.pm.ApplicationInfo, pkg: String): String {
    if (Build.VERSION.SDK_INT >= 26) {
        when (ai.category) {
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> return "Video"
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> return "Music"
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> return "Games"
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> return "News"
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> return "Photos"
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
            else -> {}
        }
    }
    if ((ai.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) return "Games"
    val p = pkg.lowercase()
    return when {
        p.contains("game") -> "Games"
        MUSIC_HINTS.any { p.contains(it) } -> "Music"
        VIDEO_HINTS.any { p.contains(it) } -> "Video"
        SOCIAL_HINTS.any { p.contains(it) } -> "Social"
        p.contains("news") -> "News"
        PHOTO_HINTS.any { p.contains(it) } -> "Photos"
        PRODUCTIVITY_HINTS.any { p.contains(it) } -> "Productivity"
        else -> "Other"
    }
}

fun tryStart(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
    Toast.makeText(ctx, "Couldn't open that on this TV", Toast.LENGTH_SHORT).show()
}

fun launchApp(ctx: Context, app: AppEntry) {
    try {
        ctx.startActivity(app.intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun openSystemSettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_SETTINGS))
fun openWifiSettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
fun openAccountSettings(ctx: Context) =
    tryStart(ctx, Intent(Settings.ACTION_SYNC_SETTINGS), Intent(Settings.ACTION_SETTINGS))
fun openHomeSettings(ctx: Context) =
    tryStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))

// ---------- Turning an app icon into a tintable glyph ----------

/** Icons are analyzed at this size, then shrunk to the exact size they're shown at. */
private const val WORK = 256

private class Glyph(val mask: Bitmap?, val bg: Color)

private fun buildIcon(d: Drawable, maskPx: Int, iconPx: Int): Built {
    val full = d.toBitmap(WORK, WORK, Bitmap.Config.ARGB_8888)
    var layered: Glyph? = null
    if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
        layered = adaptiveGlyph(d)
    }
    val glyph = layered ?: extractGlyph(full, 0)
    val mask = glyph.mask?.let { fitDown(it, maskPx).asImageBitmap() }
    return Built(fitDown(full, iconPx).asImageBitmap(), mask, glyph.bg)
}

private fun builtFor(key: String, drawable: () -> Drawable, maskPx: Int, iconPx: Int): Built {
    val cached = iconCache[key]
    if (cached != null) return cached
    val d = drawable()
    val b = try {
        buildIcon(d, maskPx, iconPx)
    } catch (e: Throwable) {
        val plain = try {
            d.toBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        } catch (e2: Throwable) {
            Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        }
        Built(plain.asImageBitmap(), null, Color.Transparent)
    }
    iconCache[key] = b
    return b
}

private fun fitDown(src: Bitmap, target: Int): Bitmap {
    val longest = max(src.width, src.height)
    if (longest <= target) return src
    val s = target.toFloat() / longest
    val tw = max(1, (src.width * s).roundToInt())
    val th = max(1, (src.height * s).roundToInt())
    return scaleDown(src, tw, th)
}

private fun dist(a: Int, b: Int): Float {
    val dr = ((a shr 16) and 255) - ((b shr 16) and 255)
    val dg = ((a shr 8) and 255) - ((b shr 8) and 255)
    val db = (a and 255) - (b and 255)
    return sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.67f
}

private fun lum(c: Int): Float =
    (0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)) / 255f

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private class Stats(
    val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
    val count: Int, val total: Int,
) {
    val bw: Int get() = maxX - minX + 1
    val bh: Int get() = maxY - minY + 1
    val fill: Float get() = count / (bw * bh).toFloat()       // how solid the shape is inside its box
    val areaFrac: Float get() = (bw * bh) / total.toFloat()   // how much of the icon the shape's box covers
    val coverage: Float get() = count / total.toFloat()
}

private fun inkStats(ink: FloatArray, w: Int, h: Int): Stats? {
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    var count = 0
    for (y in 0 until h) for (x in 0 until w) {
        if (ink[y * w + x] > 0.35f) {
            count++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    return if (count == 0) null else Stats(minX, minY, maxX, maxY, count, w * h)
}

/** A believable logo: not a speck, not the whole icon, not a solid blob. */
private fun isClean(s: Stats, maxFill: Float): Boolean =
    s.coverage in 0.012f..0.55f && s.fill <= maxFill && s.bw >= 6 && s.bh >= 6

private fun buildMask(ink: FloatArray, w: Int, h: Int, s: Stats): Bitmap {
    val pad = max(2, w / 64)
    val x0 = max(0, s.minX - pad)
    val y0 = max(0, s.minY - pad)
    val x1 = min(w - 1, s.maxX + pad)
    val y1 = min(h - 1, s.maxY + pad)
    val bw = x1 - x0 + 1
    val bh = y1 - y0 + 1
    val out = IntArray(bw * bh)
    for (y in 0 until bh) for (x in 0 until bw) {
        val a = (ink[(y + y0) * w + (x + x0)] * 255f).toInt().coerceIn(0, 255)
        out[y * bw + x] = (a shl 24) or 0xFFFFFF
    }
    val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    b.setPixels(out, 0, bw, 0, 0, bw, bh)
    return b
}

/**
 * Modern icons come in layers. The foreground layer is the logo on a transparent canvas, which is
 * exactly the glyph we want (Android 13+ icons can even ship a dedicated single-color layer).
 */
@RequiresApi(26)
private fun adaptiveGlyph(d: AdaptiveIconDrawable): Glyph? {
    val mono: Drawable? = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
    val layer: Drawable = mono ?: d.foreground ?: return null
    val size = WORK * 3 / 2 // the layer canvas is larger than the visible icon
    val bmp = layer.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    val px = IntArray(size * size)
    bmp.getPixels(px, 0, size, 0, 0, size, size)
    val ink = FloatArray(size * size)
    for (i in ink.indices) ink[i] = (px[i] ushr 24) / 255f
    val s = inkStats(ink, size, size) ?: return null
    // a solid blob means the foreground has its own tile; let the color-based method handle those
    if (!isClean(s, 0.72f)) return null
    return Glyph(buildMask(ink, size, size, s), Color.Transparent)
}

/**
 * Separates the logo from the icon's background by color, for icons that aren't layered.
 * Handles solid tiles, gradients with a light logo, transparent icons, and a logo sitting on a
 * smaller tile inside the icon. Returns mask = null if no clean glyph is found.
 */
private fun extractGlyph(bmp: Bitmap, depth: Int): Glyph {
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val bpx = max(3, min(w, h) / 40)
    val border = ArrayList<Int>()
    var transparent = 0
    var total = 0
    for (y in 0 until h) for (x in 0 until w) {
        if (x < bpx || y < bpx || x >= w - bpx || y >= h - bpx) {
            total++
            val p = px[y * w + x]
            if ((p ushr 24) < 40) transparent++ else border.add(p)
        }
    }
    val ink = FloatArray(w * h)
    var bgColor = Color.Transparent
    if (transparent > total / 2 || border.isEmpty()) {
        for (i in ink.indices) ink[i] = (px[i] ushr 24) / 255f
    } else {
        val n = border.size
        val rs = IntArray(n)
        val gs = IntArray(n)
        val bs = IntArray(n)
        for (i in 0 until n) {
            val p = border[i]
            rs[i] = (p shr 16) and 255
            gs[i] = (p shr 8) and 255
            bs[i] = p and 255
        }
        rs.sort(); gs.sort(); bs.sort()
        val bg = (0xFF shl 24) or (rs[n / 2] shl 16) or (gs[n / 2] shl 8) or bs[n / 2]
        bgColor = Color(bg)
        var spreadSum = 0f
        for (p in border) spreadSum += dist(p, bg)
        val spread = spreadSum / n
        val lb = lum(bg)
        for (i in ink.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            ink[i] = when {
                a < 0.16f -> 0f
                spread < 0.09f -> smooth(0.10f, 0.28f, dist(p, bg)) * a
                lb < 0.6f -> smooth(0.18f, 0.40f, lum(p) - lb) * a
                else -> smooth(0.18f, 0.40f, lb - lum(p)) * a
            }
        }
    }
    val s = inkStats(ink, w, h) ?: return Glyph(null, bgColor)
    if (isClean(s, 0.93f)) return Glyph(buildMask(ink, w, h, s), bgColor)

    // The "logo" is a big solid tile covering most of the icon (e.g. a white square with the real
    // logo inside it). Look inside the tile once.
    if (depth == 0 && s.fill > 0.93f && s.areaFrac in 0.35f..0.99f && s.bw > 24 && s.bh > 24) {
        val inner = Bitmap.createBitmap(bmp, s.minX, s.minY, s.bw, s.bh)
        val g = extractGlyph(inner, depth + 1)
        if (g.mask != null) return Glyph(g.mask, bgColor)
    }
    return Glyph(null, bgColor)
}
