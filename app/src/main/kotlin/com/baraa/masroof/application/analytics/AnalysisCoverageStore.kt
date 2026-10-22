package com.baraa.masroof.application.analytics

import android.content.Context
import com.baraa.masroof.data.analytics.AnalyticsDatabase
import com.baraa.masroof.data.analytics.AnalysisCoverageRow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object AnalysisCoverageStore {
    suspend fun recordScan(context: Context, after: Instant?, startedAt: Instant, zone: ZoneId = ZoneId.systemDefault()) {
        val first = after?.atZone(zone)?.let {
            it.toLocalDate().toEpochDay() + if(it.toLocalTime() == LocalTime.MIDNIGHT) 0 else 1
        } ?: 0L
        val through = startedAt.atZone(zone).toLocalDate().toEpochDay() - 1
        if(first > through) return
        val database = AnalyticsDatabase.open(context)
        try {
            database.analytics().coverage(listOf(AnalysisCoverageRow("scan:${startedAt.toEpochMilli()}:$first", first, through,
                "SCAN_RECEIPT", startedAt.toEpochMilli(), startedAt.toEpochMilli())))
        } finally { database.close() }
    }
}
