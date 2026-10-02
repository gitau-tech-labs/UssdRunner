package com.example.ussdrunner

/**
 * Shared singleton that stores the queue of USSD replies for the current session.
 * The Activity (or AutomationEngine) writes the list, the AccessibilityService
 * consumes it one step at a time.
 */
object UssdStepStore {

    @Volatile private var steps: List<String> = emptyList()
    @Volatile private var index: Int = 0

    /** True while there is at least one step not yet sent. */
    @Volatile
    var active: Boolean = false
        private set

    /** The last step that was actually typed and sent — handy for logging/UI. */
    @Volatile
    var lastSentStep: String? = null
        private set

    /** Total number of steps in the current session. */
    val total: Int get() = steps.size

    /** How many steps have been consumed so far. */
    val sentCount: Int get() = index

    /** Start a new session with the given list of steps. */
    @Synchronized
    fun begin(newSteps: List<String>) {
        steps = newSteps
        index = 0
        lastSentStep = null
        active = newSteps.isNotEmpty()
    }

    /** Return the next step and advance the cursor, or null if done. */
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

    /** True when every step has been consumed. */
    @Synchronized
    fun isDone(): Boolean = index >= steps.size

    /** Wipe the queue. */
    @Synchronized
    fun reset() {
        steps = emptyList()
        index = 0
        lastSentStep = null
        active = false
    }
}
