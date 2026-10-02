package com.example.ussdrunner

/**
 * Shared singleton that holds the queue of USSD replies for the current session.
 * The Activity (or AutomationEngine) writes the list; the AccessibilityService
 * consumes it one step at a time.
 */
object UssdStepStore {

    @Volatile private var steps: List<String> = emptyList()
    @Volatile private var index: Int = 0

    /** True while there is at least one step left to send. */
    @Volatile
    var active: Boolean = false
        private set

    /** The last step that was actually typed and sent — handy for logging/UI. */
    @Volatile
    var lastSentStep: String? = null
        private set

    val total: Int get() = steps.size
    val sentCount: Int get() = index

    /** Start a new session. */
    @Synchronized
    fun begin(newSteps: List<String>) {
        steps = newSteps
        index = 0
        lastSentStep = null
        active = newSteps.isNotEmpty()
    }

    /** Look at the next step without consuming it. */
    @Synchronized
    fun peek(): String? = steps.getOrNull(index)

    /** Return the next step and advance the cursor, or null when done. */
    @Synchronized
    fun next(): String? {
        if (index >= steps.size) {
            active = false
            return null
        }
        val s = steps[index++]
        lastSentStep = s
        return s
    }

    @Synchronized
    fun isDone(): Boolean = index >= steps.size

    @Synchronized
    fun reset() {
        steps = emptyList()
        index = 0
        lastSentStep = null
        active = false
    }
}
