// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/FloatingService.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FloatingService : Service() {

    companion object {
        private const val CHANNEL_ID = "sys_ui_svc"
        private const val NOTIF_ID = 1
        const val EXTRA_PROJECTION_RESULT = "proj_result"
        const val EXTRA_PROJECTION_DATA   = "proj_data"
    }

    private lateinit var manager: FloatingViewManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        manager = FloatingViewManager(this)
        manager.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kalau ada data screen projection, setup OCR
        val resultCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT, -1) ?: -1
        val projData   = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

        if (resultCode != -1 && projData != null) {
            val helper = ScreenCaptureHelper(this)
            helper.setup(resultCode, projData)
            manager.setScreenHelper(helper)
        }

        return START_STICKY
    }

    override fun onDestroy() { manager.remove(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "System UI", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System UI")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
}
