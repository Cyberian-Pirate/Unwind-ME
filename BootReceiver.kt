package com.unwind.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

// Backup for the prayer banner service after a reboot. Unwind is the Home
// app, so MainActivity.onCreate also starts the service when the TV boots.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return
        runCatching {
            val i = Intent(ctx, PrayerBannerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }
}
