package com.unwind.launcher

import android.service.dreams.DreamService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

// The screensaver (DreamService), built to Azuan's approved v5 mockup:
// the Bing daily photo frosted + dim, everything centered, a huge thin
// clock, date, calendar, weather and the five prayer times. All data is
// the same live data Home uses (JAKIM prayer times, Open-Meteo weather,
// the Bing wallpaper), and the accent color is the wallpaper's own
// derived accent, so the dream changes color with the daily photo like
// every other Unwind bar.
//
// Burn-in: the whole column shifts a few pixels in a random direction
// every minute, so no text sits on the same pixels all night.
//
// Pick it in Settings → Device Preferences → Screen saver. The dream is
// not interactive: any key press or touch wakes the TV, as usual.
class UnwindDreamService : DreamService() {
    // A DreamService has no Activity, so its window has no lifecycle
    // owner of its own — but ComposeView needs one to run its recomposer.
    // This tiny owner drives the composition: created when the window
    // attaches, resumed while the dream runs, destroyed when it ends.
    private val dreamLifecycleOwner = DreamLifecycleOwner()
    private var composeView: ComposeView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val view = ComposeView(this).apply { setContent { UnwindDream() } }
        ViewTreeLifecycleOwner.set(view, dreamLifecycleOwner)
        composeView = view
        setContentView(view)
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        dreamLifecycleOwner.handleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.disposeComposition()
        composeView = null
        super.onDetachedFromWindow()
    }

    private class DreamLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun handleEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}

// Inter, the closest free stand-in for San Francisco, which the v5
// mockup was drawn in. Static instances cut from the variable font, so
// no FontVariation API is needed.
private val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)

@Composable
private fun UnwindDream() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    var prayer by remember { mutableStateOf<PrayerDay?>(null) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var backdrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var accent by remember { mutableStateOf(Color(0xFF92E5F7)) }
    var shift by remember { mutableStateOf(IntOffset.Zero) }

    // Clock tick (also refreshes the next-prayer highlight as time passes).
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(15_000)
        }
    }
    // Data refresh: prayer + weather + wallpaper, every 30 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                prayer = loadPrayer(ctx)
                weather = loadWeather(ctx)
                runCatching { refreshBing(ctx) }
                val settings = loadSettings(ctx)
                val wall = loadWallpaper(ctx, settings)
                if (wall != null) {
                    accent = wall.wp.accent
                    val metrics = ctx.resources.displayMetrics
                    val aspect = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
                    backdrop = withContext(Dispatchers.Default) {
                        makeBackdrops(wall.bitmap, aspect).heavy
                    }
                }
            }
            delay(30L * 60L * 1000L)
        }
    }
    // Burn-in shift: a few pixels in a random direction, every minute.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(Random.nextInt(-12, 13), Random.nextInt(-12, 13))
        }
    }

    val nextIdx = prayer?.let { nextPrayerIndex(it.times24, now) } ?: -1
    val hour12 = when (val h = now.get(Calendar.HOUR)) {
        0 -> 12
        else -> h
    }
    val digits = "$hour12:%02d".format(now.get(Calendar.MINUTE))
    val ampm = if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

    Box(Modifier.fillMaxSize().background(Color(0xFF0A1216))) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // The dim layer: the frosted photo stays visible but never sharp.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().offset { shift },
        ) {
            Spacer(Modifier.height(34.dp))
            Text(
                dateFormat.format(now.time),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 15.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(4.dp))
            prayer?.hijri?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.47f),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(30.dp))
            // Hero clock: thin digits white, AM/PM in the accent, smaller.
            Row {
                Text(
                    digits,
                    color = Color.White.copy(alpha = 0.73f),
                    fontSize = 130.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    ampm,
                    color = accent.copy(alpha = 0.75f),
                    fontSize = 42.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(160.dp).height(1.dp).background(Color.White.copy(alpha = 0.28f)))
            Spacer(Modifier.height(12.dp))
            // Weather: icon + temperature + condition + place, one quiet row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather?.icon ?: WeatherIcon.CLOUD)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${weather?.degrees ?: "--\u00b0"}   ${weather?.text ?: "—"}  \u00b7  Seri Kembangan",
                    color = Color.White.copy(alpha = 0.63f),
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(22.dp))
            CalendarGrid(now, accent)
            Spacer(Modifier.height(18.dp))
            Text(
                "WAKTU SOLAT",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                for (i in PRAYER_NAMES.indices) {
                    val isNext = i == nextIdx
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(100.dp),
                    ) {
                        Text(
                            PRAYER_NAMES[i].uppercase(Locale.ENGLISH),
                            color = if (isNext) accent.copy(alpha = 0.75f)
                            else Color.White.copy(alpha = 0.51f),
                            fontSize = 10.sp,
                            fontFamily = Inter,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            prayer?.display?.getOrNull(i) ?: "--:--",
                            color = if (isNext) accent.copy(alpha = 0.77f)
                            else Color.White.copy(alpha = 0.69f),
                            fontSize = 14.sp,
                            fontFamily = Inter,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CalendarGrid(now: Calendar, accent: Color) {
    val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val startDow = first.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
    val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    val cells = List(startDow - 1) { null as Int? } + (1..daysInMonth).toList()
    val rows = cells.chunked(7)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            for (h in listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")) {
                Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        h,
                        color = Color.White.copy(alpha = 0.47f),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in rows) {
            Row {
                for (day in row) {
                    Box(
                        Modifier.width(36.dp).height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val isToday = day == today
                            Text(
                                day.toString(),
                                color = if (isToday) accent.copy(alpha = 0.77f)
                                else Color.White.copy(alpha = 0.63f),
                                fontSize = 12.sp,
                                fontFamily = Inter,
                                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherGlyph(icon: WeatherIcon) {
    Canvas(Modifier.width(30.dp).height(22.dp)) {
        val w = size.width
        val h = size.height
        // Cloud: three circles + a base, white.
        drawCircle(Color.White.copy(alpha = 0.67f), radius = w * 0.20f, center = Offset(w * 0.25f, h * 0.45f))
        drawCircle(Color.White.copy(alpha = 0.67f), radius = w * 0.26f, center = Offset(w * 0.50f, h * 0.35f))
        drawCircle(Color.White.copy(alpha = 0.67f), radius = w * 0.20f, center = Offset(w * 0.75f, h * 0.45f))
        drawRect(
            Color.White.copy(alpha = 0.67f),
            topLeft = Offset(w * 0.08f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.30f),
        )
        when (icon) {
            WeatherIcon.RAIN, WeatherIcon.THUNDER -> {
                val rain = Color(0xFF96C8FF).copy(alpha = 0.67f)
                for (dx in listOf(0.20f, 0.48f, 0.76f)) {
                    drawLine(rain, Offset(w * dx, h * 0.82f), Offset(w * dx - 2f, h * 0.98f), strokeWidth = 2f)
                }
            }
            WeatherIcon.SNOW -> {
                val snow = Color.White.copy(alpha = 0.67f)
                for (dx in listOf(0.22f, 0.50f, 0.78f)) {
                    drawCircle(snow, radius = 2f, center = Offset(w * dx, h * 0.90f))
                }
            }
            else -> Unit
        }
    }
}
