package com.baraa.masroof.application.credit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baraa.masroof.MainActivity
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditFacilityOverview
import com.baraa.masroof.application.maintenance.StartupMaintenanceOutcome
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Daily check: when a credit card's statement closes tomorrow (or today) and something is owed,
 * posts one reminder with the amount to pay. Each card and closing date is notified once.
 */
class CardDueReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val container = (applicationContext as MasroofApplication).container
        if (container.awaitStartupMaintenance() != StartupMaintenanceOutcome.READY) Result.retry()
        else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val overview = container.dashboardService.loadOverview(FinancialPeriodPolicy.periodContaining(today))
            CardDueReminders.publish(applicationContext, overview.creditFacilities.facilities, today)
            Result.success()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "masroof:card-due-reminder"
        private val REMIND_AT: LocalTime = LocalTime.of(8, 0)

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(REMIND_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CardDueReminderWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delay, TimeUnit.MINUTES)
                    .addTag(NAME)
                    .build(),
            )
        }
    }
}

/** Which cards to remind about, and the posting itself. */
object CardDueReminders {
    private const val CHANNEL = "masroof_card_due"
    private const val TAG = "masroof-card-due"
    private const val PREFS = "card_due_reminders"

    data class Reminder(val key: String, val last4: String, val amount: BigDecimal, val payBy: LocalDate)

    /** Cards owing a positive amount whose statement closes today or tomorrow. */
    fun due(facilities: List<CreditFacilityOverview>, today: LocalDate): List<Reminder> = facilities.mapNotNull { facility ->
        val due = facility.companionDue ?: return@mapNotNull null
        if (due.amount.amount.signum() <= 0) return@mapNotNull null
        if (due.payBy.isBefore(today) || due.payBy.isAfter(today.plusDays(1))) return@mapNotNull null
        Reminder("${facility.bank.id}:${facility.primaryLast4}:${due.payBy}", facility.primaryLast4, due.amount.amount, due.payBy)
    }

    fun publish(context: Context, facilities: List<CreditFacilityOverview>, today: LocalDate) {
        val reminders = due(facilities, today)
        if (reminders.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.cc_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (reminder in reminders) {
            if (prefs.getBoolean(reminder.key, false)) continue
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, reminder.key.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val text = context.getString(
                if (reminder.payBy == today) R.string.cc_reminder_today else R.string.cc_reminder_tomorrow,
                reminder.amount.setScale(2).toPlainString(),
                reminder.last4,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.cc_reminder_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(TAG, reminder.key.hashCode(), notification)
            } catch (_: SecurityException) {
                continue
            }
            prefs.edit().putBoolean(reminder.key, true).apply()
        }
    }
}
