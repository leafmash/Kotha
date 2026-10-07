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

    fun shortDate(ms: Long, skeleton: String): String {
        val pattern = DateFormat.getBestDateTimePattern(AppLanguage.locale(), skeleton)
        return SimpleDateFormat(pattern, AppLanguage.locale()).format(Date(ms))
    }
}
