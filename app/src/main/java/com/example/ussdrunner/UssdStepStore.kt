package com.example.ussdrunner

/**
 * Shared singleton that stores the list of USSD reply steps.
 * The Activity writes it, the AccessibilityService consumes it one step at a time.
 */
object UssdStepStore {

    @Volatile private var steps: List<String> = emptyList()
    @Volatile private var index: Int = 0

    @Volatile
    var active: Boolean = false
        private set

    fun begin(newSteps: List<String>) {
        steps = newSteps
        index = 0
        active = newSteps.isNotEmpty()
    }

    @Synchronized
    fun next(): String? {
        if (index >= steps.size) {
            active = false
            return null
        }
        return steps[index++]
    }

    fun reset() {
        steps = emptyList()
        index = 0
        active = false
    }
}
