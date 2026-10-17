package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.interpretation.LegacyInterpretationAdapter
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.*

class BetaFinancialApprovalScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val asset = OwnedAsset(AssetId(Environment.BETA, bank, "external"), AssetKind.ACCOUNT, "1234")
    private val at = Instant.parse("2026-10-17T10:30:00Z")
    @After fun cleanup() { BetaContainer.resetOffline(context) }

    private fun seed(): BetaFinancialApprovalPreview = runBlocking {
        BetaContainer.resetOffline(context)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            BetaAssetRegistry(db).register(asset)
            db.bankSelectionDao().upsert(BetaBankSelectionEntity(bank.value, "ENABLED"))
            val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "synthetic")
            val store = RoomBetaEvidenceStore(db)
            store.capture(CapturedEvidence("screen", EvidenceRequest("screen", EvidenceOrigin.EXPLICIT_SAMPLE),
                EvidenceMessage("screen", "synthetic", "synthetic only", at, ReceiptTimeSource.USER_SUPPLIED), at, route, emptyList()))
            val archive = InterpretationArchive.encode(Interpretation("screen", LegacyInterpretationAdapter.VERSION, bank,
                Facts(MessageFamily.PURCHASE, Money.of("23", Currency.USD), occurredAtLocal = LocalDateTime.parse("2026-10-17T13:30:00"),
                    exchangeRate = BigDecimal("3.756957")), ParseStatus.SUCCESS, null, emptyList(), emptySet()))
            store.begin("screen", at); store.complete("screen", route,
                EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), archive, "never authority"), at)
            requireNotNull(RoomBetaFinancialAdoption(db).preview("screen"))
        } finally { db.close() }
    }

    private fun chooseExpense() {
        compose.onNodeWithTag("approval-type-EXPENSE").performScrollTo().performClick()
        compose.onNodeWithTag("approval-source-0").performScrollTo().performClick()
        compose.onNodeWithTag("approval-destination-external").performScrollTo().performClick()
    }

    @Test fun rtlDraftSurvivesRecreationAndCancelNeverApproves() {
        val preview = seed(); var writes = 0; var cancels = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaFinancialApprovalEditor(preview, false, { cancels++ }, { writes++ }, {}, {}, {}) }
        } } }
        chooseExpense()
        compose.onNodeWithTag("approval-save").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("approval-type-EXPENSE").assertIsSelected()
        compose.onNodeWithTag("approval-source-0").assertIsSelected()
        compose.onNodeWithTag("approval-save").assertIsNotEnabled()
        compose.onNodeWithTag("approval-cancel").performScrollTo().performClick()
        assertEquals(0, writes); assertEquals(1, cancels)
    }

    @Test fun explicitConfirmationAndBusyBlockDuplicateAndDismissal() {
        val preview = seed(); var busy by mutableStateOf(false); var request: BetaFinancialApproval? = null; var cancels = 0
        compose.setContent { MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaFinancialApprovalEditor(preview, busy, { cancels++ }, { request = it; busy = true }, {}, {}, {}) }
        } }
        chooseExpense()
        compose.onNodeWithTag("approval-save").assertIsNotEnabled()
        compose.onNodeWithTag("approval-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("approval-save").performScrollTo().performClick()
        assertEquals(asset.id, request?.source); assertNull(request?.destination)
        compose.onNodeWithTag("approval-save").assertIsNotEnabled()
        compose.onNodeWithTag("approval-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("approval-type-FEE").assertIsNotEnabled()
        assertEquals(0, cancels)
    }

    @Test fun actualPanelApprovesWholeFxAndDurableRetrySurvivesReopenWithoutDuplicate() {
        seed()
        val container = BetaContainer(context)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaFinancialApprovalPanel(container, {}) } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-item-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("approval-item-0").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-save").fetchSemanticsNodes().isNotEmpty() }
            chooseExpense()
            compose.onNodeWithTag("approval-confirm").performScrollTo().performClick()
            compose.onNodeWithTag("approval-save").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-item-0").fetchSemanticsNodes().isNotEmpty() }
        } finally { container.close() }
        runBlocking {
            var db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            try {
                val adoption = requireNotNull(db.financialAdoptionDao().evidence("screen"))
                db.financialAdoptionDao().retry(BetaFinancialApprovalRetryEntity("screen", adoption.approvalRequest))
                val before = requireNotNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key("screen")))
                assertEquals(Money.of("23", Currency.USD), before.command.amount)
                assertEquals(AppliedFx(BigDecimal("3.756957"), "SMS"), before.command.appliedFx)
                db.close(); db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
                val service = RoomBetaFinancialAdoption(db)
                val exact = requireNotNull(service.preview("screen")?.pending)
                assertTrue((service.retryApproval("screen", exact) as BetaFinancialAdoptionResult.Posted).result is FinancialPostingResult.AlreadyApplied)
                assertEquals(1, RoomBetaFinancialLedger(db).history(RoomBetaFinancialAdoption.key("screen")).size)
                assertNull(service.preview("screen")?.pending)
            } finally { db.close() }
        }
    }

    @Test fun actualPanelRetriesTheReviewedDurableIntentWithoutDuplicate() {
        val initial = seed()
        runBlocking {
            val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            try {
                val service = RoomBetaFinancialAdoption(db)
                val request = requireNotNull(betaApprovalDraft(initial, "EXPENSE", "asset:external", "external", null))
                assertTrue(service.approve(request) is BetaFinancialAdoptionResult.Posted)
                val adopted = requireNotNull(db.financialAdoptionDao().evidence("screen"))
                db.financialAdoptionDao().retry(BetaFinancialApprovalRetryEntity("screen", adopted.approvalRequest))
            } finally { db.close() }
        }
        val container = BetaContainer(context)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaFinancialApprovalPanel(container, {}) } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-item-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("approval-item-0").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-retry").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("approval-retry").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-item-0").fetchSemanticsNodes().isNotEmpty() }
            assertNull(container.financialApprovalPreview("screen")?.pending)
            assertEquals(1, container.financialHistory(RoomBetaFinancialAdoption.key("screen")).size)
        } finally { container.close() }
    }

    @Test fun disabledBankBlocksLegacyPendingRetryAndExactCancelNeverPosts() {
        val initial = seed()
        runBlocking {
            val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            try {
                val original = BetaFinancialApproval("screen", initial.item.archiveRevision, null, FinancialType.EXPENSE, asset.id, null)
                RoomBetaEvidenceStore(db).begin("screen", at)
                assertTrue(RoomBetaFinancialAdoption(db).approve(original) is BetaFinancialAdoptionResult.Review)
                db.evidenceDao().clearRetry("screen")
                db.bankSelectionDao().upsert(BetaBankSelectionEntity(bank.value, "DISABLED"))
            } finally { db.close() }
        }
        val container = BetaContainer(context)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaFinancialApprovalPanel(container, {}) } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-item-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("approval-item-0").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-retry").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("approval-retry").assertIsNotEnabled()
            compose.onNodeWithTag("approval-cancel-pending").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("approval-retry").fetchSemanticsNodes().isEmpty() }
            assertNull(container.financialApprovalPreview("screen")?.pending)
            assertNull(container.financialAuditPosting(RoomBetaFinancialAdoption.key("screen")))
            assertEquals(0, container.financialHistory(RoomBetaFinancialAdoption.key("screen")).size)
        } finally { container.close() }
    }
}
