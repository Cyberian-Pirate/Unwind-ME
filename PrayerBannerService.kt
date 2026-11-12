package com.unwind.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import java.util.Calendar

// The one part of the prayer feature that still needs an overlay: the
// 60-second banner has to appear over YouTube and other apps, and only
// an overlay window can do that. It lives inside this same app, not a
// second app. Unwind is the Home app, so MainActivity.onCreate also
// starts this service when the TV boots; BootReceiver is only a backup.
class PrayerBannerService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var checkThread: Thread? = null
    @Volatile
    private var running = false

    private var bannerView: FrameLayout? = null
    private var bannerText: TextView? = null
    private var bannerScroll: Runnable? = null

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun notification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel("prayer_banner", "Prayer banner", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "prayer_banner")
        } else {
            Notification.Builder(this)
        }
        return b
            .setContentTitle("Unwind")
            .setContentText("Prayer banner active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification())
        android.util.Log.d("UnwindBanner", "service started")
        running = true
        checkThread = Thread {
            while (running) {
                try {
                    checkPrayerMinute()
                } catch (e: Exception) {
                    // a failed check must not kill the loop
                }
                try {
                    Thread.sleep(30_000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        checkThread?.interrupt()
        hideBanner()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Fires only on the exact entry minute of a fardhu prayer, once per
    // prayer per day. A minute that passed while the TV slept is not
    // shown late, because the current minute no longer matches.
    private fun checkPrayerMinute() {
        val day = loadPrayer(this) ?: return
        val now = Calendar.getInstance()
        val hm = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val idx = day.times24.indexOf(hm)
        if (idx < 0) return
        val todayKey = "%04d%02d%02d".format(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH),
        )
        val prefs = getSharedPreferences("prayer_banner", Context.MODE_PRIVATE)
        val firedKey = "$todayKey:${PRAYER_NAMES[idx]}"
        if (prefs.getBoolean(firedKey, false)) return
        prefs.edit().putBoolean(firedKey, true).apply()
        if (!Settings.canDrawOverlays(this)) return
        // The banner's look comes from Unwind's own settings, read fresh
        // here so a wallpaper or glass change in Preferences recolors
        // the next banner with no restart. Stock wallpapers need no I/O
        // (the table holds their colors); Bing and Custom go through the
        // same loader Home uses, with the stock table as fallback.
        val settings = loadSettings(this)
        val wp = if (settings.wallSource == WallSource.Stock) {
            WALLPAPERS.getOrElse(settings.wallpaper) { WALLPAPERS[0] }
        } else {
            try {
                loadWallpaper(this, settings)?.wp
            } catch (e: Exception) {
                null
            } ?: WALLPAPERS.getOrElse(settings.wallpaper) { WALLPAPERS[0] }
        }
        mainHandler.post { showBanner(PRAYER_NAMES[idx], wp.tint, settings.glass) }
    }

    // The banner is a plain View, so it cannot use the Compose glass
    // modifier; this rebuilds the same look from the same inputs (the
    // current wallpaper's tint, the current glass style). Normal: tint
    // gradient 0.34 -> 0.55 with a white top sheen and a thin rim.
    // Frost: milky white 0.22 over tint 0.22, stronger rim. Liquid:
    // faint tint 0.12 with a specular top sheen and no outline, like
    // the lens bars on Home. The old hardcoded dark bar (#80000000) is
    // gone; the banner now changes color with the wallpaper, like every
    // other Unwind bar.
    private fun bannerBackground(
        tint: androidx.compose.ui.graphics.Color,
        style: GlassStyle,
    ): android.graphics.drawable.Drawable {
        val r = dp(20f).toFloat()
        fun grad(top: Int, bottom: Int): GradientDrawable =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
                cornerRadius = r
            }
        fun solid(color: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = r
            }
        fun white(a: Float): Int = android.graphics.Color.argb((a * 255).toInt(), 255, 255, 255)
        val layers: Array<GradientDrawable> = when (style) {
            GlassStyle.Normal -> arrayOf(
                grad(tint.copy(alpha = 0.34f).toArgb(), tint.copy(alpha = 0.55f).toArgb()),
                grad(white(0.12f), android.graphics.Color.TRANSPARENT),
            )
            GlassStyle.Frost -> arrayOf(
                solid(white(0.22f)),
                solid(tint.copy(alpha = 0.22f).toArgb()),
                grad(white(0.16f), android.graphics.Color.TRANSPARENT),
            )
            GlassStyle.Liquid -> arrayOf(
                solid(tint.copy(alpha = 0.12f).toArgb()),
                grad(white(0.25f), android.graphics.Color.TRANSPARENT),
            )
        }
        val strokeA = when (style) {
            GlassStyle.Normal -> 0.20f
            GlassStyle.Frost -> 0.30f
            GlassStyle.Liquid -> 0f
        }
        if (strokeA > 0f) layers.last().setStroke(dp(1f), white(strokeA))
        return android.graphics.drawable.LayerDrawable(layers)
    }

    private fun showBanner(
        name: String,
        tint: androidx.compose.ui.graphics.Color,
        style: GlassStyle,
    ) {
        if (bannerView != null) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bg = bannerBackground(tint, style)
            val bar = FrameLayout(this).apply { background = bg }
            val text = TextView(this).apply {
                setText(bannerText(name))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setSingleLine(true)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), 0, dp(16f), 0)
                // WRAP_CONTENT inside the 350dp bar is measured AT_MOST
                // the bar width, so only the first ~40 chars of the
                // sentence are ever laid out and the tail can never
                // scroll in (same class of bug as the ticker vc104
                // fix: on this TV it cut off at "M" of Maghrib).
                // Give the view its full measured text width so the
                // whole sentence scrolls through the bar; the bar
                // clips it to its own bounds.
                val fullW = (paint.measureText(text.toString()) + dp(32f)).toInt()
                layoutParams = FrameLayout.LayoutParams(
                    fullW,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            bar.addView(text)
            val lp = WindowManager.LayoutParams(
                dp(350f),
                dp(34f),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = dp(16f)
            wm.addView(bar, lp)
            bannerView = bar
            bannerText = text
            // Scroll right to left at about 60 dp/s (2 px per 16 ms step
            // on this TV's density 2 screen), then hide after 60 seconds.
            // 30 seconds was not enough: the full sentence takes ~20 s
            // to cross the 350dp bar, so one slow pass got cut off
            // before the tail ("...disolatkan!") scrolled through
            // (Azuan, Isyak 12 Nov). 60 s allows two full passes.
            // The bar is 350dp wide and centered: on this 960dp-wide
            // screen it spans 305-655dp, clear of the top-right pill
            // (which starts around 665dp) and of the Home title.
            val textW = text.paint.measureText(text.text.toString())
            val barW = dp(350f).toFloat()
            var x = barW
            val scroll = object : Runnable {
                override fun run() {
                    val t = bannerText ?: return
                    x -= 2f
                    if (x < -textW) x = barW
                    t.translationX = x
                    mainHandler.postDelayed(this, 16)
                }
            }
            bannerScroll = scroll
            mainHandler.post(scroll)
            mainHandler.postDelayed({ hideBanner() }, 60_000)
            // Log tag is UnwindBanner (not Glass TV's TvHome.PrayerBanner),
            // so a future logcat check can tell which app's banner fired.
            android.util.Log.i("UnwindBanner", "banner shown for $name")
        } catch (e: Exception) {
            hideBanner()
        }
    }

    private fun hideBanner() {
        bannerScroll?.let { mainHandler.removeCallbacks(it) }
        bannerScroll = null
        val bar = bannerView ?: return
        bannerView = null
        bannerText = null
        android.util.Log.i("UnwindBanner", "banner hidden")
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(bar)
        } catch (e: Exception) {
            // view already gone
        }
    }
}
