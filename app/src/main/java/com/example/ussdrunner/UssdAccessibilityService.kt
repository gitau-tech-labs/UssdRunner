package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches for USSD dialogs. As soon as a *new* menu text appears, it types
 * the next queued step, presses Send, and moves on — no delays.
 *
 * The editable input field is excluded from the "menu signature" so typing a
 * step never counts as a new menu. Only real network prompts advance the flow.
 */
class UssdAccessibilityService : AccessibilityService() {

    private var lastMenuSignature: String = ""
    private var processing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!UssdStepStore.active) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        if (processing) return

        val root = rootInActiveWindow ?: return
        if (!isUssdDialog(root)) return

        val signature = extractMenuSignature(root)
        if (signature.isEmpty() || signature == lastMenuSignature) return

        // ---- New menu detected ----
        lastMenuSignature = signature
        UssdLog.append("📩 Menu: ${signature.replace("\n", " | ")}")

        val step = UssdStepStore.peek() ?: return
        processing = true
        fireStep(root, step)
        processing = false
    }

    private fun fireStep(root: AccessibilityNodeInfo, step: String) {
        val input = findInput(root)
        val send  = findSend(root)

        if (input == null) {
            UssdLog.append("⚠️ Input field missing — step '$step' skipped")
            return
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                step
            )
        }
        val typed = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        UssdLog.append(if (typed) "⌨️ Typed: $step" else "⚠️ Typing failed: $step")

        // Consume the step now that it's in the field.
        UssdStepStore.next()

        if (send == null) {
            UssdLog.append("⚠️ Send button not found")
            return
        }
        val clicked = send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        UssdLog.append(if (clicked) "✅ Sent: $step" else "⚠️ Send click failed: $step")

        if (clicked) {
            if (UssdStepStore.isDone()) {
                UssdLog.append("🏁 All steps sent — assuming success")
                AutomationEngine.onStepsComplete()
            } else {
                AutomationEngine.heartbeat()
            }
        }
    }

    // ---------- Dialog detection ----------

    private fun isUssdDialog(root: AccessibilityNodeInfo): Boolean =
        findInput(root) != null && findSend(root) != null

    // ---------- Menu signature (ignores editable field contents) ----------

    private fun extractMenuSignature(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()
        collectMenu(node, sb)
        return sb.toString().trim()
    }

    private fun collectMenu(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.isEditable) {
            val t = node.text?.toString()
            if (!t.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(t)
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectMenu(it, sb) }
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
