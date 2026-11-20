// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/ExamAccessibilityService.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ExamAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ExamAccessibilityService? = null
        private const val TYPING_DELAY_MS = 30L  // delay antar karakter, mirip manusia
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() { }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ─── Auto-type jawaban ke kolom aktif ────────────────────────────────────

    fun typeAnswer(answer: String) {
        val root = rootInActiveWindow ?: return
        val editableNode = findEditableNode(root)
        editableNode?.let { fillNode(it, answer) }
        root.recycle()
    }

    // Cari input field yang bisa diisi
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Cari node yang focused + editable
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused

        // Fallback: scan semua editable node, ambil yang pertama visible
        return findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun fillNode(node: AccessibilityNodeInfo, text: String) {
        // Fokus ke node
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Clear existing text
        val clearArgs = Bundle().apply {
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                (node.text?.length ?: 0)
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, clearArgs)

        // Set teks langsung (paling cepat)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
