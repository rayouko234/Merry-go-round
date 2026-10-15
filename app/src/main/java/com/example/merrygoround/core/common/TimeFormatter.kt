package com.example.merrygoround.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timestamps are stored as epoch millis (UTC) or epoch days for date-only values,
 * and converted to the device zone for display.
 */
object TimeFormatter {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    fun formatDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFormatter)

    fun formatShortDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(shortDateFormatter)

    fun formatDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFormatter)

    /** Formats a date-only epoch-day value (distinct from the millis overloads). */
    fun formatEpochDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(dateFormatter)

    fun formatShortEpochDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)

    fun todayEpochDay(): Long = LocalDate.now(zone).toEpochDay()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun epochMillisAtStartOfDay(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Human-friendly relative label used in activity feeds. */
    fun formatRelative(epochMillis: Long, nowMillis: Long = nowMillis()): String {
        val diff = nowMillis - epochMillis
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${diff / 60_000L} min ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            diff < 172_800_000L -> "Yesterday"
            diff < 604_800_000L -> "${diff / 86_400_000L} days ago"
            else -> formatDate(epochMillis)
        }
    }
}
