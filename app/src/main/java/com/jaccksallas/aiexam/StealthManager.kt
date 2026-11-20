// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/StealthManager.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager

object StealthManager {

    fun hideFromRecents(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun spoofTaskDescription(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            activity.setTaskDescription(
                ActivityManager.TaskDescription("System UI", null, 0xFF000000.toInt())
            )
        }
    }

    fun getOverlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR

    fun getOverlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
}
