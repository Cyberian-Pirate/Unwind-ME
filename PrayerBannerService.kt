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
import java.util.Calendar

// The one part of the prayer feature that still needs an overlay: the
// 30-second banner has to appear over YouTube and other apps, and only
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
        mainHandler.post { showBanner(PRAYER_NAMES[idx]) }
    }

    private fun showBanner(name: String) {
        if (bannerView != null) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f).toFloat()
                setColor(Color.parseColor("#80000000"))
                setStroke(dp(1f), Color.parseColor("#2EFFFFFF"))
            }
            val bar = FrameLayout(this).apply { background = bg }
            val text = TextView(this).apply {
                setText(bannerText(name))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setSingleLine(true)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), 0, dp(16f), 0)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            bar.addView(text)
            val lp = WindowManager.LayoutParams(
                dp(700f),
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
            // on this TV's density 2 screen), then hide after 30 seconds.
            val textW = text.paint.measureText(text.text.toString())
            val barW = dp(700f).toFloat()
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
            mainHandler.postDelayed({ hideBanner() }, 30_000)
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
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(bar)
        } catch (e: Exception) {
            // view already gone
        }
    }
}
