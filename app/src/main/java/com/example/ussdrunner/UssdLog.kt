package com.example.ussdrunner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared, observable log of USSD activity.
 * Activity observes it; AccessibilityService and Telephony callbacks append to it.
 */
object UssdLog {

    private const val MAX_LINES = 200

    private val _lines = MutableLiveData<List<String>>(emptyList())
    val lines: LiveData<List<String>> = _lines

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun append(msg: String) {
        val stamped = "[${timeFmt.format(Date())}] $msg"
        val current = _lines.value.orEmpty()
        val updated = (current + stamped).takeLast(MAX_LINES)
        _lines.postValue(updated)
    }

    @Synchronized
    fun clear() {
        _lines.postValue(emptyList())
    }
}
