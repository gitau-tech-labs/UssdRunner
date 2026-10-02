package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects the standard USSD dialog, logs its appearance, and — when a session
 * is active — types each queued step every 3 seconds so the user can watch.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val STEP_DELAY_MS = 3000L   // wait before typing each step
        private const val DEBOUNCE_MS   = 3500L   // quiet period after sending
        private const val TYPE_TO_SEND_MS = 800L  // pause between typing and Send
    }

    private val handler = Handler(Looper.getMainLooper())

    private var dialogVisible = false
    private var lastDialogText: String = ""
    private var debouncing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow
        val detected = root != null && isUssdDialog(root)

        // ---------- 1. Appearance / disappearance ----------
        if (detected && !dialogVisible) {
            dialogVisible = true
            UssdLog.append("🔔 DIALOG DETECTED")
        } else if (!detected && dialogVisible) {
            dialogVisible = false
            lastDialogText = ""
            UssdLog.append("🛑 DIALOG CLOSED")
            return
        }

        if (!detected || root == null) return

        // ---------- 2. Menu text ----------
        val text = extractText(root).trim()
        if (text.isNotEmpty() && text != lastDialogText) {
            lastDialogText = text
            UssdLog.append("📩 Menu: ${text.replace("\n", " | ")}")
        }

        // ---------- 3. Automation (only if a session is running) ----------
        if (!UssdStepStore.active) return
        if (debouncing) return

        val input = findInput(root) ?: return
        val send = findSend(root) ?: return

        val next = UssdStepStore.next() ?: return
        UssdLog.append("⏳ Typing in ${STEP_DELAY_MS / 1000}s: $next")

        debouncing = true
        handler.postDelayed({ typeStep(next, input) }, STEP_DELAY_MS)
        // Keep `send` reference alive in case the tree changes; we re‑find it below.
        @Suppress("UNUSED_EXPRESSION") send
    }

    // ---------- Step execution ----------

    private fun typeStep(step: String, _staleInput: AccessibilityNodeInfo) {
        val freshRoot = rootInActiveWindow
        val input = freshRoot?.let { findInput(it) }
        val send = freshRoot?.let { findSend(it) }

        if (input == null) {
            UssdLog.append("⚠️ Input field gone before typing $step")
            debouncing = false
            return
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                step
            )
        }
        val ok = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        UssdLog.append(if (ok) "⌨️ Typed: $step" else "⚠️ Could not type: $step")

        // Pause so you actually see the digit land before Send is pressed.
        handler.postDelayed({
            val r2 = rootInActiveWindow
            val s2 = r2?.let { findSend(it) }
            val clicked = s2?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            UssdLog.append(if (clicked) "✅ Sent: $step" else "⚠️ Send button not found")

            handler.postDelayed({ debouncing = false }, DEBOUNCE_MS)
        }, TYPE_TO_SEND_MS)
    }

    // ---------- Dialog detection ----------

    /**
     * A node tree is treated as a USSD dialog if it contains at least one
     * editable field AND something that looks like a Send / OK button.
     */
    private fun isUssdDialog(root: AccessibilityNodeInfo): Boolean {
        return findInput(root) != null && findSend(root) != null
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
