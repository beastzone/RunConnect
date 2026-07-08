package com.runconnect.app.utils

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object FormatUtils {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    fun formatPacePerKm(secondsPerKm: Double): String {
        if (secondsPerKm <= 0) return "--"
        val totalSeconds = secondsPerKm.roundToInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d /km".format(m, s)
    }

    fun formatPacePerMile(secondsPerMile: Double): String {
        if (secondsPerMile <= 0) return "--"
        val totalSeconds = secondsPerMile.roundToInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d /mi".format(m, s)
    }

    fun formatDistanceKm(meters: Double): String =
        "%.2f km".format(meters / 1000.0)

    fun formatDistanceMiles(meters: Double): String =
        "%.2f mi".format(meters / 1609.344)

    fun formatElevation(meters: Double): String =
        "%d m".format(meters.roundToInt())

    fun formatHeartRate(bpm: Int?): String =
        if (bpm != null) "$bpm bpm" else "--"

    fun formatCalories(kcal: Int?): String =
        if (kcal != null) "$kcal kcal" else "--"

    fun formatDate(instant: Instant): String =
        dateFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun formatFullDate(instant: Instant): String =
        fullDateFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun formatTime(instant: Instant): String =
        timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun formatSleepDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun formatRelativeDate(instant: Instant): String {
        val today = LocalDate.now()
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> dateFormatter.format(date)
            else -> fullDateFormatter.format(date)
        }
    }

    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

    fun formatShortDate(date: LocalDate): String =
        shortDateFormatter.format(date)

    fun formatRaceTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }
}
