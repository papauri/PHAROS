package org.pharos.core.mesh

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

/**
 * Passive GNSS Atomic Clock Synchronizer (1575.42 MHz, Zero RF Emission) &
 * Firefly 15-Minute / 15-Second Duty-Cycler (`:00`, `:15`, `:30`, `:45` UTC).
 */
class FireflyMeshScheduler(private val context: Context? = null) {

    companion object {
        const val INTERVAL_15_MIN_MS = 15L * 60L * 1000L // 900,000 ms
        const val BURST_WAKE_DURATION_MS = 15_000L       // 15,000 ms (1.67% duty cycle)
        const val ACTION_FIREFLY_BURST_WAKE = "org.pharos.mesh.ACTION_FIREFLY_BURST_WAKE"
    }

    @Volatile
    private var gnssOffsetMs: Long = 0L

    @Volatile
    private var lastGnssSyncElapsedMs: Long = 0L

    @Volatile
    var isGnssLocked: Boolean = false
        private set

    /**
     * Updates the local monotonic clock offset from a passive GPS/GNSS fix timestamp.
     */
    fun recordPassiveGnssFix(gnssUtcTimeMillis: Long, systemWallClockMillis: Long = System.currentTimeMillis()) {
        gnssOffsetMs = gnssUtcTimeMillis - systemWallClockMillis
        lastGnssSyncElapsedMs = try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            0L
        }
        isGnssLocked = true
    }

    /**
     * Passively queries the last known GPS_PROVIDER fix without triggering active network geolocation.
     */
    @Suppress("MissingPermission")
    fun pollPassiveGnssClockIfPermitted(): Long {
        val locMgr = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locMgr != null) {
            try {
                val loc = locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locMgr.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (loc != null && loc.time > 0) {
                    recordPassiveGnssFix(loc.time)
                }
            } catch (_: SecurityException) {
            }
        }
        return currentSynchronizedUtcMillis()
    }

    fun currentSynchronizedUtcMillis(systemNowMs: Long = System.currentTimeMillis()): Long {
        return systemNowMs + gnssOffsetMs
    }

    /**
     * Computes the exact UTC epoch millisecond timestamp of the next `:00`, `:15`, `:30`, or `:45`
     * Firefly micro-burst window.
     */
    fun computeNextFireflyBurstUtcMillis(nowUtcMs: Long = currentSynchronizedUtcMillis()): Long {
        val remainder = nowUtcMs % INTERVAL_15_MIN_MS
        return if (remainder == 0L) nowUtcMs + INTERVAL_15_MIN_MS else nowUtcMs + (INTERVAL_15_MIN_MS - remainder)
    }

    /**
     * Returns true if the given UTC timestamp falls inside an active 15-second Firefly wake window.
     */
    fun isInsideBurstWindow(nowUtcMs: Long = currentSynchronizedUtcMillis()): Boolean {
        val offsetIntoQuarterHour = nowUtcMs % INTERVAL_15_MIN_MS
        return offsetIntoQuarterHour in 0 until BURST_WAKE_DURATION_MS
    }

    /**
     * Estimates remaining battery endurance in hours given current battery percentage
     * when running Firefly 15s/15m duty cycle (~0.9% battery per hour -> ~110 hours / 4.5 days at 100%).
     */
    fun estimateFireflyBatteryEnduranceHours(batteryPercent: Int): Int {
        val clamped = batteryPercent.coerceIn(1, 100)
        return ((clamped / 100.0) * 96.0).toInt().coerceAtLeast(1)
    }

    fun scheduleNextExactWakeAlarm() {
        val ctx = context ?: return
        val alarmMgr = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextUtcMs = computeNextFireflyBurstUtcMillis()
        val intent = Intent(ctx, FireflyAlarmReceiver::class.java).apply {
            action = ACTION_FIREFLY_BURST_WAKE
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(ctx, 1515, intent, piFlags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextUtcMs, pendingIntent)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, nextUtcMs, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, nextUtcMs, pendingIntent)
        }
    }
}

class FireflyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == FireflyMeshScheduler.ACTION_FIREFLY_BURST_WAKE) {
            val svcIntent = Intent(context, FireflyForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (_: Throwable) {
            }
        }
    }
}

class FireflyForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "pharos_firefly_sync"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "PHAROS Firefly 15m Sync",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("PHAROS Firefly Burst Active")
                .setContentText("15-second P2P capsule exchange window")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
            startForeground(1501, notification)
        }
        stopSelfResult(startId)
        return START_NOT_STICKY
    }
}
