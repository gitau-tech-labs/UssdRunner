package com.example.ussdrunner

/**
 * Runtime state for the currently running keep-alive cycle.
 * Lives in the app process; read/written by both the receiver and the
 * accessibility service.
 */
object KeepAliveSession {
    @Volatile var active: Boolean = false
        private set
    @Volatile var response: String = ""
    @Volatile var lastMenu: String = ""
    @Volatile var targetSubId: Int = -1

    fun begin(subId: Int) {
        active = true
        response = ""
        lastMenu = ""
        targetSubId = subId
    }

    fun end() { active = false }

    fun cancel() { active = false }
}