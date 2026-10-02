package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches for USSD dialogs. Types the next queued step every 3 seconds
 * and presses Send, so the user can watch the session unfold.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        /** How long to wait before typing each step (ms). */
        private const val STEP_DELAY_MS = 3000L

        /** How long to ignore new events after typing, so we don't double‑fire. */
        private const val DEBOUNCE_MS = 3500L
    }

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
        if (findInput(root) == null || findSend(root) == null) return

        // ---- Log the visible dialog text (once per unique menu) ----
        val dialogText = extractText(root).trim()
        if (dialogText.isNotEmpty() && dialogText != lastDialogText) {
            lastDialogText = dialogText
            UssdLog.append("📩 Menu: ${dialogText.replace("\n", " | ")}")
        }

        val next = UssdStepStore.next() ?: return
        UssdLog.append("⏳ Waiting ${STEP_DELAY_MS / 1000}s before typing: $next")

        debouncing = true
        handler.postDelayed({ typeStep(next) }, STEP_DELAY_MS)
    }

    private fun typeStep(step: String) {
        val root = rootInActiveWindow
        val input = root?.let { findInput(it) }
        val send = root?.let { findSend(it) }

        if (input != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    step
                )
            }
            val ok = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            UssdLog.append(if (ok) "⌨️ Typed: $step" else "⚠️ Could not type: $step")
        } else {
            UssdLog.append("⚠️ Input field gone before typing $step")
        }

        // Small pause so the user sees the text land, then click Send.
        handler.postDelayed({
            val r2 = rootInActiveWindow
            val s2 = r2?.let { findSend(it) }
            val clicked = s2?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            UssdLog.append(if (clicked) "✅ Sent: $step" else "⚠️ Send button not found")

            handler.postDelayed({ debouncing = false }, DEBOUNCE_MS)
        }, 800L)
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
