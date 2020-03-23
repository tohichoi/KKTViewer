package com.soonsim.kktlogviewer

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class DateConversion {
    companion object {
        fun dateToCalendar(date: Date) : Calendar {
            val cal=Calendar.getInstance(TimeZone.getTimeZone(ZoneId.systemDefault()))
            cal.time=date
            return cal
        }

        fun dateToLocalDateTime(date: Date, zoneOffset: ZoneOffset? = null) : LocalDateTime {
            val instant: Instant = Instant.ofEpochMilli(date.time)
            val ldt: LocalDateTime = LocalDateTime.ofInstant(instant,
                zoneOffset ?: ZoneOffset.UTC
            )
            return ldt
        }

        fun localDateTimeToDate(localdatetime: LocalDateTime) : Date {
            val instant: Instant = localdatetime.atZone(ZoneId.systemDefault()).toInstant()
            val date = Date.from(instant)
            return date
        }

        fun localDateToString(s: String) : LocalDate {
            val formatter= DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val viewDate= LocalDate.parse(s, formatter)
            return viewDate
        }

        fun millsToLocalDateTime(millis: Long): LocalDateTime? {
            val instant = Instant.ofEpochMilli(millis)
            return instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

        fun localDateTimeToMilli(ldt: LocalDateTime) : Long {
            return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}