package com.unwind.launcher

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A loaded wallpaper plus the colors the UI borrows from it. */
class WallData(val bitmap: Bitmap, val image: ImageBitmap, val wp: Wp)

private const val CUSTOM_FILE = "custom_wallpaper.jpg"
private const val BING_FILE = "bing_wallpaper.jpg"

fun customFile(ctx: Context) = File(ctx.filesDir, CUSTOM_FILE)
fun bingFile(ctx: Context) = File(ctx.filesDir, BING_FILE)

// ---------- Loading ----------

fun decodeFileScaled(f: File, maxW: Int, maxH: Int): Bitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, b)
    if (b.outWidth <= 0 || b.outHeight <= 0) return null
    var sample = 1
    while (b.outWidth / (sample * 2) >= maxW && b.outHeight / (sample * 2) >= maxH) sample *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun fromFile(f: File, key: String, name: String): WallData? {
    if (!f.exists()) return null
    val bmp = decodeFileScaled(f, 1920, 1080) ?: return null
    return WallData(bmp, bmp.asImageBitmap(), deriveWp(bmp, key, name))
}

/** Loads whichever wallpaper the settings ask for, falling back to a stock one if the file is missing. */
fun loadWallpaper(ctx: Context, s: LauncherSettings): WallData? {
    val stockIdx = s.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)
    fun stock(): WallData? {
        val wp = WALLPAPERS[stockIdx]
        val bmp = decodeRes(ctx, wp.key, 1) ?: return null
        return WallData(bmp, bmp.asImageBitmap(), wp)
    }
    return when (s.wallSource) {
        WallSource.Custom -> fromFile(customFile(ctx), "custom", "My photo") ?: stock()
        WallSource.Bing -> fromFile(bingFile(ctx), "bing", "Daily wallpaper") ?: stock()
        WallSource.Stock -> stock()
    }
}

// ---------- Colors from any picture ----------

/**
 * Picks an accent, glass tint and dark shade from the picture's dominant hue so the whole UI
 * (icons, glass, highlights) matches whatever wallpaper is applied.
 */
fun deriveWp(bmp: Bitmap, key: String, name: String): Wp {
    val w = 48
    val h = 27
    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    var sx = 0.0
    var sy = 0.0
    var wt = 0.0
    var satSum = 0.0
    var luma = 0.0
    val hsv = FloatArray(3)
    for (p in px) {
        android.graphics.Color.colorToHSV(p, hsv)
        val weight = (hsv[1] * hsv[2] * hsv[2]).toDouble() + 0.001
        val a = Math.toRadians(hsv[0].toDouble())
        sx += cos(a) * weight
        sy += sin(a) * weight
        wt += weight
        satSum += hsv[1] * weight
        luma += (0.2126 * android.graphics.Color.red(p) +
            0.7152 * android.graphics.Color.green(p) +
            0.0722 * android.graphics.Color.blue(p)) / 255.0
    }
    var hue = Math.toDegrees(atan2(sy, sx)).toFloat()
    if (hue < 0f) hue += 360f
    val avgSat = if (wt > 0.0) (satSum / wt).toFloat() else 0f
    val l = (luma / px.size).toFloat().coerceIn(0f, 1f)

    // grayish pictures get a very light tint, colorful ones a stronger one
    val s = avgSat.coerceIn(0.12f, 0.60f)
    fun c(sat: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    return Wp(
        key = key,
        name = name,
        accent = c(s * 0.55f + 0.08f, 0.97f),
        tint = c(s * 0.95f, 0.40f),
        dark = c(s * 1.0f, 0.18f),
        luma = l,
    )
}

// ---------- Your own picture (from TV storage / USB) ----------

class GalleryItem(val uri: Uri, val name: String)

/** Lists pictures the TV knows about (internal storage and USB drives). Needs the pictures permission. */
fun queryImages(ctx: Context, limit: Int = 400): List<GalleryItem> {
    val out = ArrayList<GalleryItem>()
    val proj = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH,
    )
    try {
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            while (c.moveToNext() && out.size < limit) {
                val width = c.getInt(iW)
                if (width in 1..599) continue // skip tiny pictures and icons
                val id = c.getLong(iId)
                out.add(
                    GalleryItem(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        c.getString(iName) ?: "Picture",
                    ),
                )
            }
        }
    } catch (e: Exception) {
        // permission missing or storage unavailable
    }
    return out
}

fun decodeThumb(ctx: Context, uri: Uri, targetW: Int): Bitmap? {
    return try {
        val cr = ctx.contentResolver
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        if (b.outWidth <= 0) {
            null
        } else {
            var sample = 1
            while (b.outWidth / (sample * 2) >= targetW) sample *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = sample }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        }
    } catch (e: Exception) {
        null
    }
}

private fun exifDegrees(stream: InputStream): Int {
    val o = ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    return when (o) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

/** Copies the chosen picture into the launcher's own storage (scaled to TV size) so it always stays available. */
fun saveCustomWallpaper(ctx: Context, uri: Uri): Boolean {
    try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val deg = try {
            cr.openInputStream(uri)?.use { exifDegrees(it) } ?: 0
        } catch (e: Exception) {
            0
        }
        if (deg != 0) {
            val m = Matrix().apply { postRotate(deg.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val tmp = File(ctx.filesDir, "custom_tmp.jpg")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val dest = customFile(ctx)
        if (dest.exists()) dest.delete()
        return tmp.renameTo(dest)
    } catch (e: Exception) {
        return false
    }
}

// ---------- Bing image of the day ----------

class BingResult(val ok: Boolean, val changed: Boolean, val message: String)

private fun fetchText(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) UnwindLauncher")
    try {
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

private fun download(url: String, dest: File) {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 30_000
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) UnwindLauncher")
    try {
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    } finally {
        c.disconnect()
    }
}

/**
 * Checks Bing's picture of the day and downloads it if it's new. Cheap when already checked today.
 * Call from a background thread.
 */
fun refreshBing(ctx: Context): BingResult {
    val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    val file = bingFile(ctx)
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    if (file.exists() && prefs.getString("bingChecked", "") == today) {
        return BingResult(true, false, prefs.getString("bingTitle", "") ?: "")
    }
    return try {
        val country = Locale.getDefault().country
        val markets = if (country.isNullOrBlank()) listOf("en-US") else listOf("en-$country", "en-US")
        var img: JSONObject? = null
        for (m in markets) {
            try {
                val text = fetchText("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=$m")
                img = JSONObject(text).optJSONArray("images")?.optJSONObject(0)
                if (img != null) break
            } catch (e: Exception) {
                // try the next market
            }
        }
        if (img == null) throw IllegalStateException("no image info")
        val urlbase = img.optString("urlbase")
        val rel = img.optString("url").ifBlank { "${urlbase}_1920x1080.jpg" }
        val full = if (rel.startsWith("http")) rel else "https://www.bing.com$rel"
        val title = img.optString("title").ifBlank { img.optString("copyright") }
        val id = urlbase.ifBlank { rel }
        val changed = if (!file.exists() || prefs.getString("bingId", "") != id) {
            val tmp = File(ctx.filesDir, "bing_tmp.jpg")
            download(full, tmp)
            if (decodeFileScaled(tmp, 64, 64) == null) {
                tmp.delete()
                throw IllegalStateException("bad image")
            }
            if (file.exists()) file.delete()
            tmp.renameTo(file)
            true
        } else {
            false
        }
        prefs.edit().putString("bingChecked", today).putString("bingId", id).putString("bingTitle", title).apply()
        BingResult(true, changed, title)
    } catch (e: Exception) {
        BingResult(false, false, "Couldn't fetch today's wallpaper. Will try again soon.")
    }
}
