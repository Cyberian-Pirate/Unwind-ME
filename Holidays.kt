package com.unwind.launcher

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Malaysian holidays and observances beside the Home date, from the same
// public Google Calendar feed Glass TV uses ("Holidays in Malaysia", no
// key). The feed lists what Google lists: national days, regional days
// (marked "(regional holiday)" in the feed, a classification that is not
// part of the name and is dropped) and observances. Names are shown as
// the calendar gives them (Diwali, Hari Raya Puasa, Christmas Day, ...).
private const val HOLIDAY_FEED =
    "https://calendar.google.com/calendar/ical/en.malaysia%23holiday%40group.v.calendar.google.com/public/basic.ics"
private const val HOLIDAY_CACHE = "holidays.json"
private const val HOLIDAY_STALE_MS = 7L * 24 * 60 * 60 * 1000
private const val REGIONAL_SUFFIX = " (regional holiday)"

fun todayKey(now: Date = Date()): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(now)

// "November 11, 2026 • Wednesday", plus " | Christmas" when the day is a
// holiday. English full month and weekday names, as Azuan specified:
// December 25 with no holiday is "December 25, 2026 • Friday", and a
// holiday day is "November 11, 2026 • Wednesday | Christmas".
fun formatDateLine(now: Date, holiday: String?): String {
    val date = SimpleDateFormat("MMMM d, yyyy • EEEE", Locale.ENGLISH).format(now)
    return if (holiday.isNullOrBlank()) date else "$date | $holiday"
}

// Parses an ICS feed into yyyyMMdd -> display name. Only VEVENT blocks
// count (a VTIMEZONE block's dates must not leak in); a folded line is
// joined back; the same day's events join with " • " in feed order; the
// "(regional holiday)" suffix is dropped.
fun parseHolidaysIcs(ics: String): Map<String, String> {
    val unfolded = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")
    val out = LinkedHashMap<String, String>()
    val datePat = Regex("^DTSTART[^:]:(\\d{8})")
    var inEvent = false
    var date: String? = null
    var summary: String? = null
    for (line in unfolded.split("\n")) {
        when {
            line == "BEGIN:VEVENT" -> {
                inEvent = true
                date = null
                summary = null
            }
            line == "END:VEVENT" -> {
                if (inEvent && date != null && !summary.isNullOrBlank()) {
                    val prev = out[date]
                    out[date!!] = if (prev == null) summary!! else "$prev • $summary"
                }
                inEvent = false
            }
            inEvent -> {
                val m = datePat.find(line)
                if (m != null) {
                    date = m.groupValues[1]
                } else if (line.startsWith("SUMMARY:")) {
                    var s = line.substring("SUMMARY:".length)
                        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
                    if (s.endsWith(REGIONAL_SUFFIX)) {
                        s = s.substring(0, s.length - REGIONAL_SUFFIX.length)
                    }
                    summary = s
                }
            }
        }
    }
    return out
}

private fun readHolidaysCache(ctx: Context): Pair<Map<String, String>, Long> {
    return try {
        val file = File(ctx.filesDir, HOLIDAY_CACHE)
        if (!file.exists()) return emptyMap<String, String>() to 0L
        val root = JSONObject(file.readText())
        val d = root.optJSONObject("days") ?: return emptyMap<String, String>() to 0L
        val out = LinkedHashMap<String, String>()
        val keys = d.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = d.optString(k, "")
        }
        out to root.optLong("fetchedAt", 0)
    } catch (e: Exception) {
        emptyMap<String, String>() to 0L
    }
}

private fun saveHolidaysCache(ctx: Context, days: Map<String, String>) {
    try {
        val root = JSONObject()
        root.put("fetchedAt", System.currentTimeMillis())
        val d = JSONObject()
        for ((k, v) in days) d.put(k, v)
        root.put("days", d)
        File(ctx.filesDir, HOLIDAY_CACHE).writeText(root.toString())
    } catch (e: Exception) {
        // cache is only a fallback; a failed write is not fatal
    }
}

// Loads the holiday map from cache, refetching from Google's public feed
// when the cache is a week old. A failed fetch keeps the old map, so the
// date line never goes empty on a bad network day.
// Call from a background thread.
fun loadHolidays(ctx: Context): Map<String, String> {
    val (cached, fetchedAt) = readHolidaysCache(ctx)
    if (fetchedAt != 0L && System.currentTimeMillis() - fetchedAt <= HOLIDAY_STALE_MS && cached.isNotEmpty()) {
        android.util.Log.d("UnwindHolidays", "loadHolidays cached=${cached.size}")
        return cached
    }
    return try {
        val c = URL(HOLIDAY_FEED).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "text/calendar")
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        val data: ByteArray
        try {
            c.inputStream.use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n)
                        if (out.size() > 512 * 1024) throw IllegalStateException("response too large")
                    }
                    data = out.toByteArray()
                }
            }
        } finally {
            c.disconnect()
        }
        val parsed = parseHolidaysIcs(String(data, Charsets.UTF_8))
        if (parsed.isEmpty()) throw IllegalStateException("no events in feed")
        saveHolidaysCache(ctx, parsed)
        android.util.Log.d("UnwindHolidays", "loadHolidays fresh=${parsed.size}")
        parsed
    } catch (e: Exception) {
        android.util.Log.w("UnwindHolidays", "fetch failed, keeping ${cached.size} cached days: ${e.message}")
        cached
    }
}
