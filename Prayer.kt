package com.unwind.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Prayer times for Putrajaya, JAKIM zone WLY01. Same source Glass TV used.
private const val PRAYER_ZONE = "WLY01"
private const val PRAYER_URL =
    "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=week&zone=WLY01"
private const val PRAYER_CACHE = "prayer.json"
private const val DAY_MS = 24 * 60 * 60 * 1000L

val PRAYER_NAMES = listOf("Subuh", "Zohor", "Asar", "Maghrib", "Isyak")

// One day of prayer times. times24 holds "HH:mm" for the banner check,
// display holds the 12-hour text shown on the card, nextIndex is the
// next prayer that has not started yet, or -1 when all five have passed,
// hijri is the official JAKIM Hijri date ("1 Jamadilakhir 1448H") or
// null when the API did not supply one.
class PrayerDay(
    val times24: List<String>,
    val display: List<String>,
    val nextIndex: Int,
    val hijri: String?,
)

fun bannerText(name: String): String =
    "Sekarang sudah masuk waktu solat fardhu $name. Solatlah sebelum anda disolatkan!"

// 12-hour text with no leading zero on any hour ("5:48 AM", "1:00 PM"),
// as Azuan asked: Subuh used to keep its zero ("05:48 AM") from the v6
// mockup, and now matches the other times.
fun format12h(hhmm: String): String {
    val parts = hhmm.split(":")
    if (parts.size < 2) return hhmm
    val h = parts[0].toIntOrNull() ?: return hhmm
    val m = parts[1]
    return when {
        h == 0 -> "12:$m AM"
        h < 12 -> "$h:$m AM"
        h == 12 -> "12:$m PM"
        else -> "${h - 12}:$m PM"
    }
}

// JAKIM's own Hijri month names, as used on e-solat.
private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
)

// The API's "hijri" field ("1448-06-01") into display text
// ("1 Jamadilakhir 1448H"). Null when the field is absent or malformed,
// so the card hides the date instead of showing a wrong one. This is
// JAKIM's official date, not a computed conversion, so it cannot drift
// a day from the Malaysian announcement the way Umm al-Qura math can.
fun formatHijri(hijri: String): String? {
    val parts = hijri.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (y <= 0 || m !in 1..12 || d !in 1..31) return null
    return "$d ${HIJRI_MONTHS[m - 1]} ${y}H"
}

fun nextPrayerIndex(times24: List<String>, now: Calendar): Int {
    val hm = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    for (i in times24.indices) {
        if (times24[i] > hm) return i
    }
    return -1
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

// Finds today's row in the JAKIM week JSON. Returns null when today is
// not in the data, so the caller can fall back to the cache or show "--:--".
fun parsePrayerDay(json: String, today: Calendar): PrayerDay? {
    return try {
        val arr = JSONObject(json).optJSONArray("prayerTime") ?: return null
        val fmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = try {
                fmt.parse(o.optString("date"))
            } catch (e: Exception) {
                null
            } ?: continue
            val cal = Calendar.getInstance()
            cal.time = date
            if (!sameDay(cal, today)) continue
            val keys = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
            // JAKIM returns "HH:mm:ss"; keep "HH:mm" so format12h and the
            // banner check both get the shape they expect.
            val times = keys.map { o.optString(it).take(5) }
            if (times.any { it.isBlank() }) return null
            return PrayerDay(times, times.map { format12h(it) }, nextPrayerIndex(times, today), formatHijri(o.optString("hijri")))
        }
        null
    } catch (e: Exception) {
        null
    }
}

private fun fetchPrayerJson(): String? {
    return try {
        val c = URL(PRAYER_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) UnwindLauncher")
        try {
            if (c.responseCode != 200) return null
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

// Loads today from the cache when it is there, and refreshes the cache
// from the network when it is a day old or missing. A failed fetch keeps
// the cached times; when there is no cache at all the caller gets null
// and the card shows "--:--" instead of invented times.
// Call from a background thread.
fun loadPrayer(ctx: Context): PrayerDay? {
    val today = Calendar.getInstance()
    val file = File(ctx.filesDir, PRAYER_CACHE)
    var day: PrayerDay? = null
    if (file.exists()) {
        day = try {
            parsePrayerDay(file.readText(), today)
        } catch (e: Exception) {
            null
        }
    }
    val stale = day == null || System.currentTimeMillis() - file.lastModified() >= DAY_MS
    if (stale) {
        val fresh = fetchPrayerJson()
        if (fresh != null) {
            try {
                file.writeText(fresh)
            } catch (e: Exception) {
                // keep the old cache
            }
            parsePrayerDay(fresh, today)?.let { day = it }
        }
    }
    return day
}
