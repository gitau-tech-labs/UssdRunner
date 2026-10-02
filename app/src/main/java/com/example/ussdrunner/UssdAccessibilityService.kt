package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches for USSD dialogs.
 *
 *  1. Detects the dialog the moment it appears on screen and logs it.
 *  2. Captures the menu text so the LIVE FEED shows what the network returned.
 *  3. When a session is active, waits 3 s, types the next queued step,
 *     pauses 800 ms so the digit is visible, presses Send.
 *  4. Fires AutomationEngine.heartbeat() after every successful send, and
 *     AutomationEngine.scheduleCompletion() when the last step has been sent.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        /** Wait this long after the dialog appears before typing the next step (ms). */
        private const val STEP_DELAY_MS = 3000L

        /** Ignore events for this long after a send, to avoid double‑firing. */
        private const val DEBOUNCE_MS = 3500L

        /** Pause between typing the digits and clicking Send (ms). */
        private const val TYPE_TO_SEND_MS = 800L
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

        // -------- 1. Appearance / disappearance --------
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

        // -------- 2. Log the menu text (once per unique menu) --------
        val text = extractText(root).trim()
        if (text.isNotEmpty() && text != lastDialogText) {
            lastDialogText = text
            UssdLog.append("📩 Menu: ${text.replace("\n", " | ")}")
        }

        // -------- 3. Automation (only when a session is running) --------
        if (!UssdStepStore.active) return
        if (debouncing) return

        val input = findInput(root) ?: return
        val send  = findSend(root)  ?: return

        // Peek at the next step without consuming it, so we can log the wait first.
        val next = peekNextStep() ?: return

        UssdLog.append("⏳ Typing in ${STEP_DELAY_MS / 1000}s: $next")

        debouncing = true
        handler.postDelayed({ typeStep(next) }, STEP_DELAY_MS)

        // Unused here but kept for symmetry; typeStep() re-queries the tree.
        @Suppress("UNUSED_EXPRESSION") (input to send)
    }

    // ---------- Step execution ----------

    private fun typeStep(step: String) {
        // Re‑query the tree in case it changed since the delay started.
        val freshRoot = rootInActiveWindow
        val input = freshRoot?.let { findInput(it) }
        val send  = freshRoot?.let { findSend(it) }

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

        // Pause so the digit is visibly entered before we press Send.
        handler.postDelayed({
            val r2 = rootInActiveWindow
            val s2 = r2?.let { findSend(it) }
            val clicked = s2?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            UssdLog.append(if (clicked) "✅ Sent: $step" else "⚠️ Send button not found")

            // -------- AutomationEngine hooks --------
            if (clicked) {
                // Consume the step we actually sent, keeping the queue in sync.
                UssdStepStore.next()
                if (UssdStepStore.isDone()) {
                    AutomationEngine.scheduleCompletion()
                } else {
                    AutomationEngine.heartbeat()
                }
            }

            handler.postDelayed({ debouncing = false }, DEBOUNCE_MS)
        }, TYPE_TO_SEND_MS)
    }

    /** Look at the next step without consuming it (used for the "Typing in 3s" log). */
    private fun peekNextStep(): String? {
        // We haven't consumed it yet, so ask the store for the current head.
        // UssdStepStore.next() consumes, so we track the cursor differently:
        // we simply ask for the step that WOULD be sent next.
        return UssdStepStore.peek()
    }

    // ---------- Dialog detection ----------

    /** A node tree is a USSD dialog if it contains an editable field AND a Send/OK button. */
    private fun isUssdDialog(root: AccessibilityNodeInfo): Boolean =
        findInput(root) != null && findSend(root) != null

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
