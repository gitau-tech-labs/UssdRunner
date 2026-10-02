package com.example.ussdrunner

object UssdStepStore {

    @Volatile private var steps: List<String> = emptyList()
    @Volatile private var index: Int = 0

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var lastSentStep: String? = null
        private set

    val total: Int get() = steps.size
    val sentCount: Int get() = index

    @Synchronized
    fun begin(newSteps: List<String>) {
        steps = newSteps
        index = 0
        lastSentStep = null
        active = newSteps.isNotEmpty()
    }

    @Synchronized
    fun peek(): String? = steps.getOrNull(index)

    @Synchronized
    fun next(): String? {
        if (index >= steps.size) { active = false; return null }
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
