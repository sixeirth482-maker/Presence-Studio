package com.presencestudio.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * เก็บ log ในแอปเพื่อให้ผู้ใช้เปิดดูได้จาก Settings
 */
object AppLog {
    private const val TAG = "PresenceStudio"
    private const val MAX = 300
    private val lines = CopyOnWriteArrayList<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    private fun add(level: String, msg: String) {
        val line = "${timeFmt.format(Date())} [$level] $msg"
        lines.add(line)
        while (lines.size > MAX) {
            lines.removeAt(0)
        }
        when (level) {
            "E" -> Log.e(TAG, msg)
            "W" -> Log.w(TAG, msg)
            else -> Log.d(TAG, msg)
        }
    }

    fun d(msg: String) = add("D", msg)
    fun w(msg: String) = add("W", msg)
    fun e(msg: String) = add("E", msg)
    fun e(msg: String, t: Throwable?) = add("E", if (t != null) "$msg: ${t.message}" else msg)

    fun snapshot(): String = lines.joinToString("\n")

    fun clear() {
        lines.clear()
        d("log cleared")
    }
}
