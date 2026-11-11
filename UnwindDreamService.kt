package com.unwind.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

// The screensaver (DreamService), built to Azuan's approved v5 mockup:
// the Bing daily photo frosted + dim, everything centered, a huge thin
// clock, date, calendar, weather and the five prayer times. All data is
// the same live data Home uses (JAKIM prayer times, Open-Meteo weather,
// the Bing wallpaper), and the accent color is the wallpaper's own
// derived accent, so the dream changes color with the daily photo like
// every other Unwind bar.
//
// Plain Android Views, not Compose: a dream has no Activity, so a
// ComposeView would need a lifecycle owner wired into its window by
// hand, and that plumbing failed the CI build three times. Views need
// none of it, and they are lighter on this 2 GB TV.
//
// Burn-in: the whole column shifts a few pixels in a random direction
// every minute, so no text sits on the same pixels all night.
//
// Pick it in Settings → Device Preferences → Screen saver. The dream is
// not interactive: any key press or touch wakes the TV, as usual.
class UnwindDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var density: Float = 1f
    private lateinit var content: LinearLayout
    private lateinit var bgImage: ImageView
    private lateinit var dateView: TextView
    private lateinit var hijriView: TextView
    private lateinit var clockDigits: TextView
    private lateinit var clockAmpm: TextView
    private lateinit var weatherGlyph: WeatherGlyphView
    private lateinit var weatherView: TextView
    private lateinit var calendarBox: LinearLayout
    private lateinit var prayerRow: LinearLayout

    private var prayer: PrayerDay? = null
    private var weather: WeatherNow? = null
    private var accentArgb: Int = Color.rgb(146, 229, 247)

    private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock(Calendar.getInstance())
            handler.postDelayed(this, 15_000)
        }
    }
    private val burnInTick = object : Runnable {
        override fun run() {
            content.translationX = Random.nextInt(-12, 13).toFloat()
            content.translationY = Random.nextInt(-12, 13).toFloat()
            handler.postDelayed(this, 60_000)
        }
    }
    private val dataTick = object : Runnable {
        override fun run() {
            refreshData()
            handler.postDelayed(this, 30L * 60L * 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        density = resources.displayMetrics.density
        setContentView(buildView())
        refreshData()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(clockTick)
        handler.post(burnInTick)
        handler.post(dataTick)
        updateClock(Calendar.getInstance())
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(burnInTick)
        handler.removeCallbacks(dataTick)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    // ---------- view construction (positions match the v5 mockup, /2 = dp) ----------

    private fun dp(n: Int): Int = (n * density).roundToInt()

    private fun text(
        sizeSp: Float,
        @FontRes font: Int,
        color: Int,
    ): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = ResourcesCompat.getFont(this@UnwindDreamService, font)
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun spacer(hDp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(hDp),
        )
    }

    private fun buildView(): View {
        val root = FrameLayout(this)
        bgImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.FIT_XY
        }
        root.addView(bgImage)
        // The dim layer: the frosted photo stays visible but never sharp.
        root.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            alpha = 0.68f
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setPadding(0, dp(20), 0, 0)
        }
        dateView = text(15f, R.font.inter_regular, Color.argb(165, 255, 255, 255))
        hijriView = text(12f, R.font.inter_regular, Color.argb(120, 255, 255, 255))
        content.addView(dateView)
        content.addView(spacer(4))
        content.addView(hijriView)
        content.addView(spacer(16))

        // Hero clock: thin digits white, AM/PM in the accent, smaller.
        // baselineAligned lines the two sizes up on the same baseline.
        val clockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            isBaselineAligned = true
        }
        clockDigits = text(130f, R.font.inter_light, Color.argb(185, 255, 255, 255)).apply {
            // Trim the font's extra top/bottom padding: at 130sp the
            // TextView was 372px tall, which pushed the prayer times
            // off the bottom of the screen. Glyphs stay 130sp.
            includeFontPadding = false
        }
        clockAmpm = text(42f, R.font.inter_light, accentArgb).apply {
            includeFontPadding = false
        }
        clockRow.addView(clockDigits)
        clockRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(9), 1)
        })
        clockRow.addView(clockAmpm)
        content.addView(clockRow)
        content.addView(spacer(18))
        content.addView(View(this).apply {
            setBackgroundColor(Color.argb(70, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(dp(160), 1).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        content.addView(spacer(12))

        // Weather: icon + temperature + condition + place, one quiet row.
        // The row is full-width (vertical parent gives MATCH_PARENT by
        // default), so its gravity must center the children, or the
        // whole row sits on the left.
        val weatherRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        weatherGlyph = WeatherGlyphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(22))
        }
        weatherView = text(14f, R.font.inter_regular, Color.argb(160, 255, 255, 255))
        weatherRow.addView(weatherGlyph)
        weatherRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 1)
        })
        weatherRow.addView(weatherView)
        content.addView(weatherRow)
        content.addView(spacer(14))

        calendarBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(calendarBox)
        content.addView(spacer(12))
        content.addView(text(9f, R.font.inter_medium, Color.argb(115, 255, 255, 255)).apply {
            text = "WAKTU SOLAT"
        })
        content.addView(spacer(6))
        prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(prayerRow)
        content.addView(spacer(8))
        root.addView(content)
        return root
    }

    // ---------- updates ----------

    private fun updateClock(now: Calendar) {
        dateView.text = dateFormat.format(now.time)
        hijriView.text = prayer?.hijri ?: ""
        hijriView.visibility = if (prayer?.hijri == null) View.GONE else View.VISIBLE
        val hour12 = when (val h = now.get(Calendar.HOUR)) {
            0 -> 12
            else -> h
        }
        clockDigits.text = "$hour12:%02d".format(now.get(Calendar.MINUTE))
        clockAmpm.text = if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        buildCalendar(now)
        buildPrayerRow(now)
    }

    private fun buildCalendar(now: Calendar) {
        calendarBox.removeAllViews()
        val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val startDow = first.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        for (h in listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")) {
            header.addView(text(11f, R.font.inter_medium, Color.argb(120, 255, 255, 255)).apply {
                text = h
                layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        calendarBox.addView(header)
        calendarBox.addView(spacer(6))
        val cells = List(startDow - 1) { null as Int? } + (1..daysInMonth).toList()
        for (row in cells.chunked(7)) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            for (day in row) {
                val cell = text(
                    12f,
                    if (day == today) R.font.inter_medium else R.font.inter_regular,
                    if (day == today) accentArgb else Color.argb(160, 255, 255, 255),
                ).apply {
                    text = day?.toString() ?: ""
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(20))
                }
                rowView.addView(cell)
            }
            calendarBox.addView(rowView)
        }
    }

    private fun buildPrayerRow(now: Calendar) {
        prayerRow.removeAllViews()
        val nextIdx = prayer?.let { nextPrayerIndex(it.times24, now) } ?: -1
        for (i in PRAYER_NAMES.indices) {
            val isNext = i == nextIdx
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            col.addView(text(
                10f,
                if (isNext) R.font.inter_bold else R.font.inter_regular,
                if (isNext) accentArgb else Color.argb(130, 255, 255, 255),
            ).apply { text = PRAYER_NAMES[i].uppercase(Locale.ENGLISH) })
            col.addView(spacer(2))
            col.addView(text(
                14f,
                if (isNext) R.font.inter_bold else R.font.inter_medium,
                if (isNext) accentArgb else Color.argb(175, 255, 255, 255),
            ).apply { text = prayer?.display?.getOrNull(i) ?: "--:--" })
            prayerRow.addView(col)
        }
    }

    private fun refreshData() {
        Thread {
            val freshPrayer = loadPrayer(this)
            val freshWeather = loadWeather(this)
            runCatching { refreshBing(this) }
            val settings = loadSettings(this)
            val wall = loadWallpaper(this, settings)
            var backdrop: Bitmap? = null
            var accent: Int? = null
            if (wall != null) {
                accent = wall.wp.accent.toArgb()
                val metrics = resources.displayMetrics
                val aspect = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
                backdrop = makeBackdrops(wall.bitmap, aspect).heavy
            }
            handler.post {
                prayer = freshPrayer
                weather = freshWeather
                if (accent != null) {
                    accentArgb = accent
                    clockAmpm.setTextColor(Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent)))
                }
                if (backdrop != null) bgImage.setImageBitmap(backdrop)
                weatherGlyph.icon = weather?.icon ?: WeatherIcon.CLOUD
                weatherGlyph.invalidate()
                weatherView.text =
                    "${weather?.degrees ?: "--\u00b0"}   ${weather?.text ?: "—"}  \u00b7  Seri Kembangan"
                updateClock(Calendar.getInstance())
            }
        }.start()
    }
}

// The small weather icon from the v5 mockup: a cloud, with rain lines,
// snow dots, or nothing under it depending on the condition.
private class WeatherGlyphView(ctx: Context) : View(ctx) {
    var icon: WeatherIcon = WeatherIcon.CLOUD
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 150, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawCircle(w * 0.25f, h * 0.45f, w * 0.20f, cloudPaint)
        canvas.drawCircle(w * 0.50f, h * 0.35f, w * 0.26f, cloudPaint)
        canvas.drawCircle(w * 0.75f, h * 0.45f, w * 0.20f, cloudPaint)
        canvas.drawRect(w * 0.08f, h * 0.45f, w * 0.92f, h * 0.75f, cloudPaint)
        when (icon) {
            WeatherIcon.RAIN, WeatherIcon.THUNDER -> {
                for (dx in listOf(0.20f, 0.48f, 0.76f)) {
                    canvas.drawLine(w * dx, h * 0.82f, w * dx - 2f, h * 0.98f, rainPaint)
                }
            }
            WeatherIcon.SNOW -> {
                for (dx in listOf(0.22f, 0.50f, 0.78f)) {
                    canvas.drawCircle(w * dx, h * 0.90f, 2f * resources.displayMetrics.density, snowPaint)
                }
            }
            else -> Unit
        }
    }
}
