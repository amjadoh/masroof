package com.baraa.masroof.lab.application

import java.time.LocalDate
import java.time.ZoneId

/** Both calendar days are inclusive; SMS provider upper bound is next local midnight. */
data class LabDateWindow(val fromInclusive: Long, val toExclusive: Long) {
    fun contains(epochMillis: Long) = epochMillis >= fromInclusive && epochMillis < toExclusive
    companion object {
        fun of(from: String, to: String, zoneId: String): LabDateWindow {
            val start = LocalDate.parse(from)
            val end = LocalDate.parse(to)
            require(!end.isBefore(start)) { "تاريخ النهاية يسبق البداية" }
            val zone = ZoneId.of(zoneId)
            return LabDateWindow(start.atStartOfDay(zone).toInstant().toEpochMilli(),
                end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
    }
}
