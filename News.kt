package com.unwind.launcher

import android.content.Context
import android.util.Xml
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

// Same four feeds Glass TV's ticker used. Sinar Harian failed both feed
// paths there and is not included. CNN's own RSS stopped updating in
// 2024, so CNN comes from Google News' CNN-only feed.
private class NewsSource(val urls: List<String>)

private val NEWS_SOURCES = listOf(
    NewsSource(listOf("https://www.bharian.com.my/feed", "https://www.bharian.com.my/rss")),
    NewsSource(listOf("https://www.utusan.com.my/feed/")),
    NewsSource(listOf("https://news.google.com/rss/search?q=site:cnn.com&hl=en-US&gl=US&ceid=US:en")),
    NewsSource(listOf("https://www.aljazeera.com/xml/rss/all.xml")),
)

private const val PER_SOURCE = 5
private const val NEWS_CACHE = "news.json"

private fun fetchFeedText(url: String): String? {
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) UnwindLauncher")
        try {
            if (c.responseCode != 200) return null
            val out = StringBuilder()
            val buf = CharArray(16 * 1024)
            c.inputStream.bufferedReader().use { r ->
                var n: Int
                while (r.read(buf).also { n = it } > 0) {
                    out.append(buf, 0, n)
                    if (out.length > 512 * 1024) return null
                }
            }
            out.toString()
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

// Titles from <item><title> (RSS) or <entry><title> (Atom), in document
// order, at most max. The channel title itself is skipped because only
// titles inside an item or entry are collected.
fun parseFeedTitles(xml: String, max: Int): List<String> {
    val out = ArrayList<String>()
    try {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        var inItem = false
        var titleBuf: StringBuilder? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < max) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = p.name
                    if (name == "item" || name == "entry") inItem = true
                    else if (inItem && name == "title") titleBuf = StringBuilder()
                }
                // A title can arrive in several text chunks (entities split
                // it), so accumulate and add it once at the closing tag.
                XmlPullParser.TEXT -> {
                    titleBuf?.append(p.text)
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "title") {
                        val t = titleBuf?.toString()?.trim().orEmpty()
                        if (t.isNotEmpty()) out.add(t)
                        titleBuf = null
                    }
                    if (p.name == "item" || p.name == "entry") inItem = false
                }
            }
            event = p.next()
        }
    } catch (e: Exception) {
        // malformed feed: keep whatever was collected
    }
    return out
}

private fun readNewsCache(ctx: Context): List<String> {
    return try {
        val file = File(ctx.filesDir, NEWS_CACHE)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i)
            if (t.isNotBlank()) out.add(t)
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveNewsCache(ctx: Context, titles: List<String>) {
    try {
        val arr = JSONArray()
        for (t in titles) arr.put(t)
        File(ctx.filesDir, NEWS_CACHE).writeText(arr.toString())
    } catch (e: Exception) {
        // cache is only a fallback; a failed write is not fatal
    }
}

// Fetches up to 5 titles from each of the 4 sources, 20 at most. A failed
// fetch keeps the old titles instead of emptying the ticker.
// Call from a background thread.
fun loadNews(ctx: Context): List<String> {
    val fresh = ArrayList<String>()
    for (src in NEWS_SOURCES) {
        for (url in src.urls) {
            val xml = fetchFeedText(url) ?: continue
            val got = parseFeedTitles(xml, PER_SOURCE)
            if (got.isNotEmpty()) {
                fresh.addAll(got)
                break
            }
        }
    }
    if (fresh.isNotEmpty()) {
        saveNewsCache(ctx, fresh)
        return fresh
    }
    return readNewsCache(ctx)
}
