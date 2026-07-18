package com.sigverage.app.model

import java.text.SimpleDateFormat
import java.util.Locale

/** User-selectable time display mode. */
enum class TimeFormat {
    System,
    TwelveHour,
    TwentyFourHour;

    companion object {
        fun fromString(s: String?): TimeFormat =
            entries.firstOrNull { it.name == s } ?: System
    }
}

/**
 * Returns a [SimpleDateFormat] based on the user's preferred formats.
 */
fun getUiDateTimeFormatter(
    timeFormat: TimeFormat,
    dateFormat: DateFormat,
    includeSeconds: Boolean = false,
    locale: Locale = Locale.getDefault()
): SimpleDateFormat {
    val datePattern = dateFormat.pattern ?: "dd MMM yyyy"
    val timePattern = when (timeFormat) {
        TimeFormat.System -> {
            // Very simplified system format fallback.
            "HH:mm"
        }
        TimeFormat.TwelveHour -> if (includeSeconds) "h:mm:ss a" else "h:mm a"
        TimeFormat.TwentyFourHour -> if (includeSeconds) "HH:mm:ss" else "HH:mm"
    }
    return SimpleDateFormat("$datePattern, $timePattern", locale)
}

/** User-selectable date display mode. */
enum class DateFormat(val pattern: String?) {
    System(null),
    DayMonthYearSlash("dd/MM/yyyy"),
    MonthDayYearSlash("MM/dd/yyyy"),
    YearMonthDayDash("yyyy-MM-dd"),
    DayMonthYearText("dd MMM yyyy");

    companion object {
        fun fromString(s: String?): DateFormat =
            entries.firstOrNull { it.name == s } ?: System
    }
}
