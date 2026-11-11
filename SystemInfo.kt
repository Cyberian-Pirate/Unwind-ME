package com.unwind.launcher

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat

fun hasPerm(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

// ---------- Wi-Fi ----------

class WifiNet(
    val ssid: String,
    val level: Int,
    val secured: Boolean,
    val connected: Boolean,
    val band: String,
)

class WifiState(val connectedSsid: String?, val ethernet: Boolean, val nets: List<WifiNet>)

fun readWifi(ctx: Context): WifiState {
    val app = ctx.applicationContext
    var ethernet = false
    try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    } catch (e: Exception) {
        // ignore
    }
    var connected: String? = null
    val nets = ArrayList<WifiNet>()
    if (hasPerm(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
        try {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            try {
                val raw = wifi.connectionInfo?.ssid
                if (raw != null && raw != "<unknown ssid>") connected = raw.trim('"')
            } catch (e: Exception) {
                // can't read the current network
            }
            // Ask for a fresh scan. Each step has its own try so one failure can't hide the others.
            try {
                @Suppress("DEPRECATION")
                wifi.startScan()
            } catch (e: Exception) {
                // scanning is throttled or not allowed; we still read the last results below
            }
            try {
                val best = HashMap<String, android.net.wifi.ScanResult>()
                for (r in wifi.scanResults) {
                    @Suppress("DEPRECATION")
                    val s = r.SSID
                    if (s.isNullOrBlank()) continue
                    val old = best[s]
                    if (old == null || r.level > old.level) best[s] = r
                }
                for ((s, r) in best) {
                    val caps = r.capabilities ?: ""
                    val secured = caps.contains("WPA") || caps.contains("WEP") || caps.contains("SAE")
                    val band = when {
                        r.frequency >= 5900 -> "6 GHz"
                        r.frequency >= 4900 -> "5 GHz"
                        r.frequency > 0 -> "2.4 GHz"
                        else -> ""
                    }
                    nets.add(WifiNet(s, r.level, secured, s == connected, band))
                }
            } catch (e: Exception) {
                // no scan results available
            }
        } catch (e: Exception) {
            // Wi-Fi service missing
        }
    }
    nets.sortWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.level })
    val c = connected
    if (c != null && nets.none { it.connected }) nets.add(0, WifiNet(c, -50, true, true, ""))
    return WifiState(connected, ethernet, nets)
}

// ---------- TV inputs ----------

class InputItem(val id: String, val label: String, val type: Int, val passthrough: Boolean)

// Apps that register a "TV input" but aren't a real source you'd switch to.
private val HIDDEN_INPUT_PACKAGES = setOf("com.google.android.videos")

private fun inputGroup(i: InputItem): Int = when {
    i.type == TvInputInfo.TYPE_HDMI -> 0
    i.passthrough -> 1
    else -> 2
}

private fun inputNumber(label: String): Int =
    Regex("\\d+").find(label)?.value?.toIntOrNull() ?: 0

fun readInputs(ctx: Context): List<InputItem> {
    return try {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val all = tim.tvInputList
            .filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_TUNER }
            .filter { (it.serviceInfo?.packageName ?: "") !in HIDDEN_INPUT_PACKAGES }
            .map { info ->
                val custom = info.loadCustomLabel(ctx)?.toString()
                val label = if (custom.isNullOrBlank()) info.loadLabel(ctx).toString() else custom
                InputItem(info.id, label.trim(), info.type, info.isPassthroughInput)
            }
        // same name twice (e.g. two "Google Play Movies") -> keep one
        val seen = HashSet<String>()
        all.filter { seen.add(it.label.lowercase()) }
            .sortedWith(
                compareBy<InputItem> { inputGroup(it) }
                    .thenBy { inputNumber(it.label) }
                    .thenBy { it.label.lowercase() },
            )
    } catch (e: Exception) {
        emptyList()
    }
}

fun switchInput(ctx: Context, item: InputItem) {
    val uri = if (item.passthrough) TvContract.buildChannelUriForPassthroughInput(item.id)
    else TvContract.Channels.CONTENT_URI
    tryStart(ctx, Intent(Intent.ACTION_VIEW, uri))
}

// ---------- Google account ----------

class AcctState(val emails: List<String>, val name: String?, val photo: ImageBitmap?)

fun readAccount(ctx: Context): AcctState {
    val emails = try {
        AccountManager.get(ctx).getAccountsByType("com.google").map { it.name }
    } catch (e: Exception) {
        emptyList<String>()
    }
    var name: String? = null
    var photo: ImageBitmap? = null
    try {
        val cols = arrayOf(ContactsContract.Profile.DISPLAY_NAME, ContactsContract.Profile.PHOTO_URI)
        ctx.contentResolver.query(ContactsContract.Profile.CONTENT_URI, cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)?.takeIf { it.isNotBlank() }
                val uri = c.getString(1)
                if (uri != null) {
                    photo = ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                        BitmapFactory.decodeStream(it)
                    }?.asImageBitmap()
                }
            }
        }
    } catch (e: Exception) {
        // no permission, or this TV has no profile card
    }
    return AcctState(emails, name, photo)
}

// ---------- Home screen replacement ----------

/** True when Unwind is the app that opens when you press the Home button. */
fun isDefaultHome(ctx: Context): Boolean {
    return try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == ctx.packageName
    } catch (e: Exception) {
        false
    }
}
