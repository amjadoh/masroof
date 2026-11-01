package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.parsing.model.ParsedEventDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReconciliationTimingTest : ReconciliationTestFixture() {
    @Test
    fun reprocessInAnotherDeviceZone_keepsAlJaziraOccurredAt() = runBlocking {
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        val local = java.time.LocalDateTime.of(2026, 8, 26, 22, 30)
        persistEvent(
            smsId = "sms-zone",
            event = event(
                id = "pe-zone",
                rawSmsId = "sms-zone",
                family = MessageFamily.PURCHASE,
                amount = money("12.00"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.ONLINE,
                merchant = "Shop",
            ),
            details = ParsedEventDetails(occurredAtLocal = local),
        )
        val tokyo = serviceIn(java.time.ZoneId.of("Asia/Tokyo"))
        tokyo.reconcileStoredEvents()
        val first = ftRepo.findByRawSmsId("sms-zone")!!
        assertEquals(local.atZone(java.time.ZoneId.of("Asia/Riyadh")).toInstant(), first.occurredAt)
        assertEquals("Asia/Riyadh", first.occurredAtZone)

        val newYork = serviceIn(java.time.ZoneId.of("America/New_York"))
        newYork.reconcileStoredEvents()
        val again = ftRepo.findByRawSmsId("sms-zone")!!
        assertEquals(first.occurredAt, again.occurredAt)
        assertEquals("Asia/Riyadh", again.occurredAtZone)
    }

    @Test
    fun unknownBank_persistsFirstZoneAcrossReprocess() = runBlocking {
        val other = Bank("OTHER")
        confirmation.confirmCardOwned(CardReference(other, "1111"))
        val local = java.time.LocalDateTime.of(2026, 8, 26, 22, 30)
        persistEvent(
            smsId = "sms-other-zone",
            event = event(
                id = "pe-other-zone",
                rawSmsId = "sms-other-zone",
                family = MessageFamily.PURCHASE,
                amount = money("8.00"),
                bank = other,
                card = CardReference(other, "1111"),
                channel = PurchaseChannel.ONLINE,
            ),
            details = ParsedEventDetails(occurredAtLocal = local),
        )
        serviceIn(java.time.ZoneId.of("Asia/Tokyo")).reconcileStoredEvents()
        val first = ftRepo.findByRawSmsId("sms-other-zone")!!
        assertEquals("Asia/Tokyo", first.occurredAtZone)
        assertEquals(local.atZone(java.time.ZoneId.of("Asia/Tokyo")).toInstant(), first.occurredAt)

        serviceIn(java.time.ZoneId.of("America/New_York")).reconcileStoredEvents()
        val again = ftRepo.findByRawSmsId("sms-other-zone")!!
        assertEquals(first.occurredAt, again.occurredAt)
        assertEquals("Asia/Tokyo", again.occurredAtZone)
    }

    @Test
    fun fullReconcile_healsStalePairKeepingUnknownBankZoneWhenDeviceDefaultDiffers() = runBlocking {
        val previous = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
        try {
            val other = Bank("OTHER")
            confirmation.confirmAccountOwned(AccountReference(other, "3001"))
            val local = java.time.LocalDateTime.parse("2026-08-01T12:00:00")
            val tokyo = java.time.ZoneId.of("Asia/Tokyo")
            val outAt = Instant.parse("2026-08-01T12:00:00Z")
            val inAt = outAt.plus(TransactionMatcher.TRANSFER_MATCH_WINDOW.multipliedBy(3))
            persistEvent(
                smsId = "sms-out-zone-heal",
                at = outAt,
                details = ParsedEventDetails(occurredAtLocal = local),
                event = event(
                    id = "pe-out-zone-heal",
                    rawSmsId = "sms-out-zone-heal",
                    family = MessageFamily.TRANSFER_OUT,
                    amount = money("90.00"),
                    bank = other,
                    source = AccountReference(other, "3001"),
                    destination = AccountReference(other, "3003"),
                    network = BankNetworkType.INTRA_BANK,
                ),
            )
            serviceIn(tokyo).reconcileAffectedRawSmsIds(listOf("sms-out-zone-heal"))
            assertEquals("Asia/Tokyo", ftRepo.listAll().single().occurredAtZone)

            confirmation.confirmAccountOwned(AccountReference(other, "3003"))
            persistEvent(
                smsId = "sms-in-zone-heal",
                at = inAt,
                details = ParsedEventDetails(occurredAtLocal = local),
                event = event(
                    id = "pe-in-zone-heal",
                    rawSmsId = "sms-in-zone-heal",
                    family = MessageFamily.TRANSFER_IN,
                    amount = money("90.00"),
                    bank = other,
                    source = AccountReference(other, "3001"),
                    destination = AccountReference(other, "3003"),
                    network = BankNetworkType.INTRA_BANK,
                ),
            )
            // Full maintenance revisits posted legs outside the new message's device-zone window.
            serviceIn(java.time.ZoneId.of("America/New_York")).reconcileStoredEventsDetailed()

            val healed = ftRepo.listAll().single()
            assertEquals(FinancialTransactionType.SELF_TRANSFER, healed.type)
            assertEquals(local.atZone(tokyo).toInstant(), healed.occurredAt)
            assertEquals("Asia/Tokyo", healed.occurredAtZone)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

    @Test
    fun stalePairHeal_usesPersistedZoneWhenCounterpartHasNoLocalTime() = runBlocking {
        val previous = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
        try {
            val other = Bank("OTHER")
            confirmation.confirmAccountOwned(AccountReference(other, "3001"))
            val local = java.time.LocalDateTime.parse("2026-08-01T12:00:00")
            val tokyo = java.time.ZoneId.of("Asia/Tokyo")
            val tokyoInstant = local.atZone(tokyo).toInstant()
            val counterpartReceivedAt = tokyoInstant.plusSeconds(5 * 60)
            persistEvent(
                smsId = "sms-out-mixed-zone",
                at = Instant.parse("2026-08-01T12:00:00Z"),
                details = ParsedEventDetails(occurredAtLocal = local),
                event = event(
                    id = "pe-out-mixed-zone",
                    rawSmsId = "sms-out-mixed-zone",
                    family = MessageFamily.TRANSFER_OUT,
                    amount = money("90.00"),
                    bank = other,
                    source = AccountReference(other, "3001"),
                    destination = AccountReference(other, "3003"),
                    network = BankNetworkType.INTRA_BANK,
                ),
            )
            serviceIn(tokyo).reconcileAffectedRawSmsIds(listOf("sms-out-mixed-zone"))
            val posted = ftRepo.listAll().single()
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, posted.type)
            assertEquals("Asia/Tokyo", posted.occurredAtZone)
            assertEquals(tokyoInstant, posted.occurredAt)

            confirmation.confirmAccountOwned(AccountReference(other, "3003"))
            persistEvent(
                smsId = "sms-in-mixed-zone",
                at = counterpartReceivedAt,
                details = ParsedEventDetails(),
                event = event(
                    id = "pe-in-mixed-zone",
                    rawSmsId = "sms-in-mixed-zone",
                    family = MessageFamily.TRANSFER_IN,
                    amount = money("90.00"),
                    bank = other,
                    source = AccountReference(other, "3001"),
                    destination = AccountReference(other, "3003"),
                    network = BankNetworkType.INTRA_BANK,
                ),
            )
            serviceIn(java.time.ZoneId.of("America/New_York")).reconcileAffectedRawSmsIds(listOf("sms-in-mixed-zone"))

            val healed = ftRepo.listAll().single()
            assertEquals(FinancialTransactionType.SELF_TRANSFER, healed.type)
            assertEquals(tokyoInstant, healed.occurredAt)
            assertEquals("Asia/Tokyo", healed.occurredAtZone)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

}
