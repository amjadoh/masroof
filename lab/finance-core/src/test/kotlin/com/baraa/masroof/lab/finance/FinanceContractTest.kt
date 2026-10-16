package com.baraa.masroof.lab.finance

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.AssetKind
import com.baraa.masroof.lab.finance.asset.CreditCardRole
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.dashboard.AccountSummary
import com.baraa.masroof.lab.finance.dashboard.DashboardQuery
import com.baraa.masroof.lab.finance.dashboard.DashboardReader
import com.baraa.masroof.lab.finance.dashboard.DashboardSnapshot
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Environment
import com.baraa.masroof.lab.finance.value.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FinanceContractTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private fun id(localId: String, bankId: BankId = bank) = AssetId(Environment.BETA, bankId, localId)

    @Test fun moneyPreservesProductionMagnitudeCurrencyAndRounding() {
        assertEquals(BigDecimal("1.00"), Money.of("1.005", Currency.SAR).amount)
        assertEquals(BigDecimal("1.02"), Money.of("1.015", Currency.SAR).amount)
        assertEquals(Money.of("1", Currency.SAR), Money.of("1.00", Currency.SAR))
        assertNotEquals(Money.of("1", Currency.USD), Money.of("1", Currency.SAR))
        assertThrows(IllegalArgumentException::class.java) { Money.of("-1", Currency.SAR) }
    }

    @Test fun suffixNeverBecomesAssetIdentity() {
        val first = OwnedAsset(id("card-a"), AssetKind.CREDIT_CARD, "1234", id("facility-a"))
        val second = OwnedAsset(id("card-b"), AssetKind.CREDIT_CARD, "1234", id("facility-b"))
        assertNotEquals(first.id, second.id)
        assertNotEquals(id("card-a"), id("card-a", BankId("D360")))
        assertNotEquals(id("card-a"), id("card-a").copy(environment = Environment.MIGRATION_REHEARSAL))
        assertThrows(IllegalArgumentException::class.java) { BankId("UNKNOWN") }
    }

    @Test fun supplementaryCardsShareFacilityWithoutDuplicatingDue() {
        val facility = OwnedAsset(id("facility"), AssetKind.CREDIT_FACILITY)
        val primary = OwnedAsset(id("primary"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.PRIMARY)
        val supplementary = OwnedAsset(id("supplementary"), AssetKind.CREDIT_CARD, "5678", facility.id,
            CreditCardRole.SUPPLEMENTARY)
        assertEquals(primary.parentId, supplementary.parentId)
        assertNotEquals(primary.creditCardRole, supplementary.creditCardRole)
        assertNull(OwnedAsset(id("unlinked-mada"), AssetKind.DEBIT_CARD, "9999").parentId)
        assertThrows(IllegalArgumentException::class.java) {
            OwnedAsset(id("mada"), AssetKind.DEBIT_CARD, "1234", id("account", BankId("D360")))
        }
    }

    @Test fun metadataCorrectionDoesNotGrantAutomaticFinancialAuthority() {
        listOf(CorrectionAuthority.NONE, CorrectionAuthority.METADATA_ONLY).forEach { authority ->
            assertEquals(
                PostingEligibility.Review(setOf(ReviewReason.VALIDATION)),
                PostingGate.evaluate(false, authority, FinancialType.EXPENSE, emptySet()),
            )
        }
    }

    @Test fun explicitFinancialCorrectionStillRequiresResolvedOwnership() {
        listOf(CorrectionAuthority.CONFIRMED_FAMILY_OR_AMOUNT, CorrectionAuthority.USER_FINANCIAL_TYPE)
            .forEach { authority ->
                assertEquals(PostingEligibility.Eligible,
                    PostingGate.evaluate(false, authority, FinancialType.EXPENSE, emptySet()))
                assertEquals(PostingEligibility.Review(setOf(ReviewReason.OWNERSHIP)),
                    PostingGate.evaluate(false, authority, FinancialType.EXPENSE, setOf(ReviewReason.OWNERSHIP)))
            }
    }

    @Test fun validatedUnknownAndAmbiguousResultsRemainReviewable() {
        assertEquals(
            PostingEligibility.Review(setOf(ReviewReason.UNKNOWN_TYPE, ReviewReason.AMBIGUOUS_MATCH)),
            PostingGate.evaluate(true, CorrectionAuthority.NONE, FinancialType.UNKNOWN,
                setOf(ReviewReason.AMBIGUOUS_MATCH)),
        )
    }

    @Test fun retryIsNoOpAndOldReparseCannotUndoNewCorrection() {
        val persisted = PostingRevision(correction = 2, interpretation = 5)
        assertEquals(RevisionAction.APPLY, PostingRevisionPolicy.action(null, persisted))
        assertEquals(RevisionAction.ALREADY_APPLIED, PostingRevisionPolicy.action(persisted, persisted))
        assertEquals(RevisionAction.STALE,
            PostingRevisionPolicy.action(persisted, PostingRevision(correction = 1, interpretation = 100)))
        assertEquals(RevisionAction.APPLY,
            PostingRevisionPolicy.action(persisted, PostingRevision(correction = 3, interpretation = 1)))
    }

    @Test fun postingRetainsFxPrecisionAndPersistedTimeZone() {
        val time = TransactionTime(Instant.parse("2026-10-16T09:00:00Z"), ZoneId.of("Asia/Riyadh"))
        val command = PostingCommand(PostingKey(Environment.BETA, "tx-1"), PostingRevision(0, 1),
            FinancialType.EXPENSE, Money.of("10", Currency.USD), time, id("account"), null,
            setOf("sms-1", "sms-2"), AppliedFx(BigDecimal("3.751234"), "SMS"))
        assertEquals(BigDecimal("3.751234"), command.appliedFx!!.rateToSar)
        assertEquals(time, command.time)
        assertEquals(2, command.evidenceIds.size)
        assertThrows(IllegalArgumentException::class.java) {
            command.copy(key = command.key.copy(environment = Environment.MIGRATION_REHEARSAL))
        }
    }

    @Test fun dashboardReadContractKeepsMissingBalanceAndEnvironmentExplicit() = runTest {
        val query = DashboardQuery(Environment.BETA, Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val reader = DashboardReader { request ->
            DashboardSnapshot(request, listOf(AccountSummary(id("account"), null)), emptyList(), emptyList())
        }
        assertNull(reader.read(query).accounts.single().balance)
        assertThrows(IllegalArgumentException::class.java) {
            DashboardSnapshot(query.copy(environment = Environment.MIGRATION_REHEARSAL),
                listOf(AccountSummary(id("account"), null)), emptyList(), emptyList())
        }
    }
}
