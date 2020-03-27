package com.soonsim.kktlogviewer

import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class DateConversion {
    companion object {
        fun dateToCalendar(date: Date): Calendar {
            val cal = Calendar.getInstance(TimeZone.getTimeZone(ZoneId.systemDefault()))
            cal.time = date
            return cal
        }

        fun dateToLocalDateTime(date: Date, zoneOffset: ZoneOffset? = null): LocalDateTime {
            val instant: Instant = Instant.ofEpochMilli(date.time)
            val ldt: LocalDateTime = LocalDateTime.ofInstant(
                instant,
                zoneOffset ?: ZoneOffset.UTC
            )
            return ldt
        }

        fun localDateTimeToDate(localdatetime: LocalDateTime): Date {
            val instant: Instant = localdatetime.atZone(ZoneId.systemDefault()).toInstant()
            val date = Date.from(instant)
            return date
        }

        fun localDateToString(s: String): LocalDate {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val viewDate = LocalDate.parse(s, formatter)
            return viewDate
        }

        fun millsToLocalDateTime(millis: Long): LocalDateTime? {
            val instant = Instant.ofEpochMilli(millis)
            return instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

        fun localDateTimeToMilli(ldt: LocalDateTime): Long {
            return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        fun getISOString(date: Date): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }

        fun getDateFromString(s: String): Date? {
            // 2018년 12월 5일 오후 4:49
            val fmt =
                DateTimeFormatter.ofPattern("yyyy'년' MM'월' dd'일' a HH:mm", Locale.getDefault())
            val datetime = try {
                Date.from(LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant())
            } catch (e: DateTimeParseException) {
                return null
            }

            return datetime
        }

    }
}