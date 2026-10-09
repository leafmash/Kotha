package com.kotha.app.util

import android.content.Context
import android.text.format.DateFormat
import com.kotha.app.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

object Format {

    fun number(value: Long): String = NumberFormat.getInstance(AppLanguage.locale()).format(value)

    fun number(value: Int): String = number(value.toLong())

    fun clock(ms: Long): String {
        if (ms <= 0) return ""
        val pattern = DateFormat.getBestDateTimePattern(AppLanguage.locale(), "hm")
        return SimpleDateFormat(pattern, AppLanguage.locale()).format(Date(ms))
    }

    fun dayStart(ms: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = ms
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun dayLabel(context: Context, ms: Long): String {
        val today = dayStart(System.currentTimeMillis())
        val day = dayStart(ms)
        return when {
            day == today -> context.getString(R.string.common_today)
            day == dayStart(today - 1) -> context.getString(R.string.common_yesterday)
            else -> shortDate(ms, "d MMMM")
        }
    }

    fun listTime(ms: Long): String {
        if (ms <= 0) return ""
        return if (dayStart(ms) == dayStart(System.currentTimeMillis())) clock(ms) else shortDate(ms, "d MMM")
    }

    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val formatter = NumberFormat.getInstance(AppLanguage.locale())
        formatter.minimumIntegerDigits = 2
        return number(total / 60) + ":" + formatter.format(total % 60)
    }

    fun fileSize(bytes: Long): String {
        val locale = AppLanguage.locale()
        return when {
            bytes < 1024L -> number(bytes) + " B"
            bytes < 1024L * 1024L -> String.format(locale, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(locale, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun shortDate(ms: Long, skeleton: String): String {
        val pattern = DateFormat.getBestDateTimePattern(AppLanguage.locale(), skeleton)
        return SimpleDateFormat(pattern, AppLanguage.locale()).format(Date(ms))
    }
}
