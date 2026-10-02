package com.example.ussdrunner

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Fires every USSD step the instant a new menu appears.
 *
 *  - ONE tree traversal per event (menu text + input node + send node found together).
 *  - No debouncing, no artificial delay.
 *  - Menu signature excludes the editable field, so typing a step
 *    never counts as a new menu — only real network prompts do.
 */
class UssdAccessibilityService : AccessibilityService() {

    private companion object {
        val INPUT_IDS = setOf(
            "com.android.phone:id/input_field",
            "com.android.phone:id/inputField",
            "com.android.phone:id/ussd_input",
            "com.android.phone:id/ussdInput",
            "com.android.phone:id/input",
            "com.android.dialer:id/input",
            "com.android.server.telecom:id/input"
        )
        val SEND_IDS = setOf(
            "com.android.phone:id/buttonSend",
            "com.android.phone:id/sendButton",
            "com.android.phone:id/button_ok",
            "com.android.phone:id/ok",
            "com.android.phone:id/positiveButton",
            "android:id/button1"
        )
        val SEND_TEXTS = setOf("SEND", "OK", "SUBMIT", "CONTINUE", "REPLY")
    }

    private var lastMenuSignature: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!UssdStepStore.active) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> { /* continue */ }
            else -> return
        }

        val root = rootInActiveWindow ?: return

        // Single-pass analysis: menu text + input node + send node.
        val a = analyze(root) ?: return
        if (a.menu.isEmpty() || a.menu == lastMenuSignature) return

        // New menu — type and send the next step right now.
        lastMenuSignature = a.menu
        UssdLog.append("📩 Menu: ${a.menu.replace("\n", " | ")}")

        val step = UssdStepStore.peek() ?: return
        fireStep(a, step)
    }

    // ---------- One traversal, everything we need ----------

    private class Analysis(
        val menu: String,
        val input: AccessibilityNodeInfo,
        val send: AccessibilityNodeInfo
    )

    private fun analyze(root: AccessibilityNodeInfo): Analysis? {
        val sb = StringBuilder()
        var input: AccessibilityNodeInfo? = null
        var send: AccessibilityNodeInfo? = null
        var inputIsIdMatch = false
        var sendIsIdMatch = false

        fun visit(node: AccessibilityNodeInfo) {
            // ---- Menu text (skip the editable field) ----
            if (!node.isEditable) {
                val t = node.text?.toString()
                if (!t.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(t)
                }
            }

            // ---- Input candidate ----
            if (node.isEnabled) {
                val vid = node.viewIdResourceName
                if (input == null || !inputIsIdMatch) {
                    if (vid != null && vid in INPUT_IDS) {
                        input = node; inputIsIdMatch = true
                    } else if (input == null && node.isEditable) {
                        input = node
                    }
                }

                // ---- Send candidate ----
                if (send == null || !sendIsIdMatch) {
                    if (vid != null && vid in SEND_IDS) {
                        send = node; sendIsIdMatch = true
                    } else if (send == null || !sendIsIdMatch) {
                        val txt = node.text?.toString()?.trim()?.uppercase()
                        if (txt != null && txt in SEND_TEXTS) send = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { visit(it) }
            }
        }

        visit(root)

        val i = input ?: return null
        val s = send ?: return null
        return Analysis(sb.toString().trim(), i, s)
    }

    // ---------- Fire immediately ----------

    private fun fireStep(a: Analysis, step: String) {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                step
            )
        }
        val typed = a.input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        UssdLog.append(if (typed) "⌨️ Typed: $step" else "⚠️ Typing failed: $step")

        UssdStepStore.next()

        val clicked = a.send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    override fun onInterrupt() { /* no-op */ }
}
