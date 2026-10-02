package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UssdAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var debouncing = false
    private var lastDialogText: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!UssdStepStore.active) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        if (debouncing) return

        val root = rootInActiveWindow ?: return
        val input = findInput(root) ?: return
        val send = findSend(root) ?: return

        // ---- Capture the visible dialog text for the live feed ----
        val dialogText = extractText(root).trim()
        if (dialogText.isNotEmpty() && dialogText != lastDialogText) {
            lastDialogText = dialogText
            UssdLog.append("📩 Menu: ${dialogText.replace("\n", " | ")}")
        }

        val next = UssdStepStore.next() ?: return

        debouncing = true
        handler.postDelayed({
            val freshRoot = rootInActiveWindow
            val freshInput = freshRoot?.let { findInput(it) }
            val freshSend = freshRoot?.let { findSend(it) }

            if (freshInput != null) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        next
                    )
                }
                val ok = freshInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                UssdLog.append(if (ok) "⌨️ Typed: $next" else "⚠️ Could not type: $next")
            } else {
                UssdLog.append("⚠️ Input field disappeared before typing $next")
            }

            val clicked = freshSend?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            UssdLog.append(if (clicked) "✅ Sent: $next" else "⚠️ Send button not found")

            handler.postDelayed({ debouncing = false }, 600)
        }, 300)
    }

    // ---------- Text extraction ----------

    private fun extractText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()
        collectText(node, sb)
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(t)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    // ---------- Node discovery ----------

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = listOf(
            "com.android.phone:id/input_field",
            "com.android.phone:id/inputField",
            "com.android.phone:id/ussd_input",
            "com.android.phone:id/ussdInput",
            "com.android.phone:id/input",
            "com.android.dialer:id/input",
            "com.android.server.telecom:id/input"
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        return findEditable(root)
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = listOf(
            "com.android.phone:id/buttonSend",
            "com.android.phone:id/sendButton",
            "com.android.phone:id/button_ok",
            "com.android.phone:id/ok",
            "com.android.phone:id/positiveButton",
            "android:id/button1"
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        val texts = listOf("Send", "SEND", "OK", "Ok", "Submit", "Continue")
        for (t in texts) {
            root.findAccessibilityNodeInfosByText(t)?.firstOrNull()?.let { return it }
        }
        return null
    }

    override fun onInterrupt() { /* no-op */ }
}
