package com.chronie.homemoney.ui.expense

import android.content.Context
import com.chronie.homemoney.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun formatRelativeDate(dateString: String, context: Context): String {
    try {
        val date = LocalDate.parse(dateString)
        val today = LocalDate.now()
        val daysBetween = ChronoUnit.DAYS.between(date, today)
        
        val dayOfWeek = date.dayOfWeek
        val weekdayString = when (dayOfWeek) {
            DayOfWeek.MONDAY -> context.getString(R.string.monday)
            DayOfWeek.TUESDAY -> context.getString(R.string.tuesday)
            DayOfWeek.WEDNESDAY -> context.getString(R.string.wednesday)
            DayOfWeek.THURSDAY -> context.getString(R.string.thursday)
            DayOfWeek.FRIDAY -> context.getString(R.string.friday)
            DayOfWeek.SATURDAY -> context.getString(R.string.saturday)
            DayOfWeek.SUNDAY -> context.getString(R.string.sunday)
        }
        
        return when {
            daysBetween == 0L -> "${context.getString(R.string.date_today)}（$weekdayString）"
            daysBetween == 1L -> "${context.getString(R.string.date_yesterday)}（$weekdayString）"
            daysBetween in 2..6 -> "${context.getString(R.string.date_days_ago, daysBetween)}（$weekdayString）"
            else -> "$dateString（$weekdayString）"
        }
    } catch (e: Exception) {
        return dateString
    }
}

fun formatDateByLocale(dateString: String, locale: String): String {
    try {
        val date = LocalDate.parse(dateString)
        
        return when (locale) {
            "en" -> {
                val monthNames = arrayOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                "${monthNames[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}"
            }
            "zh", "zh-CN", "zh-HK", "zh-MO", "zh-SG", "zh-TW" -> {
                "${date.year}年${String.format("%02d", date.monthValue)}月${String.format("%02d", date.dayOfMonth)}日"
            }
            else -> {
                "${date.year}-${String.format("%02d", date.monthValue)}-${String.format("%02d", date.dayOfMonth)}"
            }
        }
    } catch (e: Exception) {
        return dateString
    }
}

fun formatMonthLabelByLocale(dateString: String, locale: String): String {
    try {
        val date = LocalDate.parse(dateString)
        
        return when (locale) {
            "zh", "zh-CN", "zh-HK", "zh-MO", "zh-SG", "zh-TW" -> {
                "${date.year}年${String.format("%02d", date.monthValue)}月"
            }
            "en" -> {
                val monthNames = arrayOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                "${monthNames[date.monthValue - 1]} ${date.year}"
            }
            else -> {
                "${date.year}-${String.format("%02d", date.monthValue)}"
            }
        }
    } catch (e: Exception) {
        return dateString
    }
}
