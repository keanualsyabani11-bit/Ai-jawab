// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/MainActivity.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_OVERLAY    = 101
        private const val REQ_PROJECTION = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StealthManager.hideFromRecents(this)
        StealthManager.spoofTaskDescription(this)
        setContentView(R.layout.activity_main)

        updateStatus()

        // Jawab soal otomatis — aktifkan overlay + screen capture
        findViewById<TextView>(R.id.btnJawabOtomatis).setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> requestOverlay()
                else -> requestScreenCapture()
            }
        }

        // Jawaban diisi AI — cek accessibility
        findViewById<TextView>(R.id.btnJawabanAI).setOnClickListener {
            if (!isAccessibilityEnabled()) {
                openAccessibilitySettings()
            } else {
                launchService(withProjection = false)
                Toast.makeText(this, "Auto-fill aktif", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto nilai di atas KKM — langsung launch
        findViewById<TextView>(R.id.btnAutoNilai).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) requestOverlay()
            else {
                launchService(withProjection = false)
                Toast.makeText(this, "Score tracker aktif", Toast.LENGTH_SHORT).show()
            }
        }

        // Stop
        findViewById<TextView>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = isAccessibilityEnabled()

        findViewById<TextView>(R.id.tvStatusOverlay).apply {
            text = if (overlay) "Overlay: Aktif" else "Overlay: Belum diizinkan"
            setTextColor(if (overlay) 0xFF00D68F.toInt() else 0xFFFF6B6B.toInt())
        }
        findViewById<TextView>(R.id.tvStatusAccess).apply {
            text = if (accessibility) "Auto-fill: Aktif" else "Auto-fill: Belum diizinkan"
            setTextColor(if (accessibility) 0xFF00D68F.toInt() else 0xFFFF6B6B.toInt())
        }
    }

    private fun launchService(withProjection: Boolean, projResultCode: Int = -1, projData: Intent? = null) {
        val i = Intent(this, FloatingService::class.java).apply {
            if (withProjection && projData != null) {
                putExtra(FloatingService.EXTRA_PROJECTION_RESULT, projResultCode)
                putExtra(FloatingService.EXTRA_PROJECTION_DATA, projData)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
        moveTaskToBack(true)
    }

    private fun requestOverlay() {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            REQ_OVERLAY
        )
    }

    private fun requestScreenCapture() {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpManager.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "Aktifkan 'AI Exam Auto-Fill' di Accessibility", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains(packageName)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) requestScreenCapture()
            }
            REQ_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    launchService(withProjection = true, projResultCode = resultCode, projData = data)
                }
            }
        }
    }
}
