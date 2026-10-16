package com.baraa.masroof.lab.beta.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaAssetDiscoveryStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "full")
    private fun archive(id: String, suffix: String) = InterpretationArchive.encode(Interpretation(id, InterpretationVersion(1, "test", "test"), bank,
        Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, suffix)), ParseStatus.PARTIAL, null,
        listOf(Evidence("source", "source", if (suffix == "5678") 5 else 0, if (suffix == "5678") 9 else 4)), emptySet()))
    private inline fun <T> withDatabase(block: (BetaDatabase) -> T): T {
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        return try { block(db) } finally { db.close() }
    }
    private fun save(id: String, suffix: String, baselineSuffix: String = suffix) = runBlocking {
        withDatabase { db ->
            val store = RoomBetaEvidenceStore(db)
            store.capture(CapturedEvidence(id, EvidenceRequest("test", EvidenceOrigin.EXPLICIT_SAMPLE),
                EvidenceMessage(id, "AlJazira", "1234 5678 synthetic", Instant.EPOCH, ReceiptTimeSource.USER_SUPPLIED), Instant.EPOCH, route,
                listOf(UserBankSelection(bank, BankSelectionState.ENABLED))))
            store.begin(id, Instant.EPOCH)
            store.complete(id, route, EvidenceComparison(EvidenceDisposition.REVIEW, emptySet(), archive(id, baselineSuffix), archive(id, suffix)), Instant.EPOCH)
        }
    }
    @Test fun explicitConfirmationIsIdempotentAndStaleDisabledOrUnconfirmedRequestsFailClosed() {
        BetaContainer.resetOffline(context)
        save("one", "1234")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val proposal = beta.discoveryPage().proposals.single()
            val draft = ManualAssetDraft(proposal.id, AssetKind.ACCOUNT, proposal.last4, ownedConfirmed = true)
            assertTrue(beta.assets().isEmpty())
            assertThrows(IllegalArgumentException::class.java) { beta.confirmProposal(proposal, draft.copy(ownedConfirmed = false)) }
            beta.confirmProposal(proposal, draft)
            beta.confirmProposal(proposal, draft)
            assertEquals(1, beta.assets().size)
            beta.backup()
            beta.setBankEnabled(bank, false)
            assertTrue(beta.discoveryPage().proposals.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { beta.confirmProposal(proposal, draft) }
            assertTrue(beta.restore())
            assertEquals(proposal.copy(confirmedAssetId = proposal.id), beta.discoveryPage().proposals.single())
            assertEquals(1, beta.assets().size)
            // Same evidence identity, newly compared observation: never overwrite prior confirmed asset.
            save("one", "5678")
            assertThrows(IllegalArgumentException::class.java) { beta.confirmProposal(proposal, draft) }
            val revised = beta.discoveryPage().proposals.single()
            assertEquals(proposal.id, revised.id)
            assertNotEquals(proposal.revision, revised.revision)
            assertEquals("1234", beta.assets().single().last4)
            beta.reset()
            assertTrue(beta.discoveryPage().proposals.isEmpty())
            assertTrue(beta.assets().isEmpty())
        }
        BetaContainer.resetOffline(context)
    }
    private fun aliases(): List<Pair<String, String>> = withDatabase { db ->
        db.openHelper.readableDatabase.query("SELECT value,evidenceId FROM beta_asset_alias ORDER BY evidenceId").use { cursor ->
            buildList<Pair<String, String>> { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
        }
    }
    @Test fun explicitCorrectionRetainsObservedAndManualProvenanceAcrossRetryEditBackupRestore() {
        BetaContainer.resetOffline(context)
        save("capture|with|delimiter", "1234")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val proposal = beta.discoveryPage().proposals.single()
            val corrected = ManualAssetDraft(proposal.id, AssetKind.ACCOUNT, "9999", ownedConfirmed = true)
            beta.confirmProposal(proposal, corrected); beta.confirmProposal(proposal, corrected)
            val originalAliases = aliases()
            assertEquals(2, originalAliases.size)
            assertTrue(originalAliases.contains("9999" to "manual-owned-confirmation"))
            val provenance = originalAliases.single { it.second.startsWith("proposal-confirmed-v1|") }
            assertEquals("1234", provenance.first)
            val segments = provenance.second.split('|')
            assertEquals(4, segments.size)
            assertEquals(proposal.evidenceId, String(java.util.Base64.getUrlDecoder().decode(segments[2]), Charsets.UTF_8))
            assertEquals(proposal.revision, segments[3])
            beta.backup()
            val existing = beta.assets().single()
            beta.saveManualAsset(corrected.copy(last4 = "5678", editing = true, expectedOriginal = existing))
            assertTrue(aliases().contains(provenance))
            assertFalse(aliases().contains("9999" to "manual-owned-confirmation"))
            assertTrue(beta.restore())
            assertEquals(originalAliases, aliases())
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun differentEvidenceExplicitlyLinksExistingAssetAndStableBindingSurvivesRecompare() {
        BetaContainer.resetOffline(context)
        save("message-one", "1234"); save("message-two", "1234")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val proposals = beta.discoveryPage().proposals
            val existingId = AssetId(Environment.BETA, bank, "existing")
            val otherId = AssetId(Environment.BETA, bank, "other")
            val existing = beta.saveManualAsset(ManualAssetDraft(existingId, AssetKind.ACCOUNT, "1234", ownedConfirmed = true))
            val other = beta.saveManualAsset(ManualAssetDraft(otherId, AssetKind.ACCOUNT, "1234", ownedConfirmed = true))
            assertThrows(IllegalArgumentException::class.java) { beta.linkExistingProposal(proposals.first(), existing, false) }
            beta.linkExistingProposal(proposals.first(), existing, true)
            beta.linkExistingProposal(proposals.last(), existing, true)
            beta.linkExistingProposal(proposals.first(), existing, true)
            assertEquals(2, beta.assets().size)
            assertTrue(beta.discoveryPage().proposals.all { it.confirmedAssetId == existingId })
            assertThrows(IllegalArgumentException::class.java) { beta.linkExistingProposal(proposals.first(), other, true) }
            assertThrows(IllegalArgumentException::class.java) { beta.confirmProposal(proposals.first(), ManualAssetDraft(proposals.first().id, AssetKind.ACCOUNT, "1234", ownedConfirmed = true)) }
            save("message-one", "5678")
            val changed = beta.discoveryPage().proposals.first()
            assertEquals(existingId, changed.confirmedAssetId)
            assertNotEquals(proposals.first().revision, changed.revision)
            assertThrows(IllegalArgumentException::class.java) { beta.linkExistingProposal(proposals.first(), existing, true) }
            val different = beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "different"), AssetKind.ACCOUNT, "5678", ownedConfirmed = true))
            assertThrows(IllegalArgumentException::class.java) { beta.linkExistingProposal(changed, different, true) }
            assertThrows(IllegalArgumentException::class.java) { beta.confirmProposal(changed, ManualAssetDraft(changed.id, AssetKind.ACCOUNT, "5678", ownedConfirmed = true)) }
            assertEquals("1234", beta.assets().single { it.id == existingId }.last4)
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun accountAndCardRolesWithinOneCaptureKeepIndependentExplicitBindings() = runBlocking {
        BetaContainer.resetOffline(context)
        val rawId = "same-capture"
        val interpretation = Interpretation(rawId, InterpretationVersion(1, "test", "test"), bank,
            Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, "1234"), card = CardReference(bank, "1234"), cardChannel = CardChannel.CREDIT),
            ParseStatus.PARTIAL, null, listOf(Evidence("source", "source", 0, 4), Evidence("card", "card", 5, 9), Evidence("credit", "card.credit", 10, 16)), emptySet())
        withDatabase { db ->
            val store = RoomBetaEvidenceStore(db)
            store.capture(CapturedEvidence(rawId, EvidenceRequest("test", EvidenceOrigin.EXPLICIT_SAMPLE), EvidenceMessage("provider", "AlJazira", "1234 1234 credit", Instant.EPOCH, ReceiptTimeSource.USER_SUPPLIED), Instant.EPOCH, route, listOf(UserBankSelection(bank, BankSelectionState.ENABLED))))
            store.begin(rawId, Instant.EPOCH)
            val json = InterpretationArchive.encode(interpretation)
            store.complete(rawId, route, EvidenceComparison(EvidenceDisposition.REVIEW, emptySet(), json, json), Instant.EPOCH)
        }
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val proposals = beta.discoveryPage().proposals
            assertEquals(2, proposals.size)
            val account = proposals.single { it.kind == AssetKind.ACCOUNT }
            val card = proposals.single { it.kind == AssetKind.CREDIT_CARD }
            assertNotEquals(account.id, card.id)
            beta.confirmProposal(account, ManualAssetDraft(account.id, AssetKind.ACCOUNT, "1234", ownedConfirmed = true))
            val facility = beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "facility"), AssetKind.CREDIT_FACILITY, "", ownedConfirmed = true))
            beta.confirmProposal(card, ManualAssetDraft(card.id, AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.PRIMARY, ownedConfirmed = true))
            assertEquals(setOf(account.id, card.id), beta.discoveryPage().proposals.map { it.confirmedAssetId }.toSet())
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun actualSavedEngineEvidenceCanSuggestWithoutAnyFinancialWrite() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            beta.importEvidence(EvidenceRequest("synthetic", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(EvidenceMessage("provider", "AlJazira",
                "POS Purchase\nCard: 1234\nAmount: 42 SAR\nAvailable balance: 100 SAR", Instant.parse("2026-10-16T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED)))
            assertEquals(AssetKind.CREDIT_CARD, beta.discoveryPage().proposals.single().kind)
            assertTrue(beta.assets().isEmpty())
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun boundedPageDoesNotMergeEqualSuffixOrAcceptBaselineDisagreement() {
        BetaContainer.resetOffline(context)
        save("a", "1234"); save("b", "1234"); save("c", "1234", "9999")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val first = beta.discoveryPage(limit = 1)
            val second = beta.discoveryPage(first.afterId!!, 1)
            assertNotEquals(first.proposals.single().id, second.proposals.single().id)
            val third = beta.discoveryPage(second.afterId!!, 1)
            assertTrue(third.proposals.isEmpty()); assertEquals(1, third.needsReview)
            assertTrue(beta.assets().isEmpty())
        }
        BetaContainer.resetOffline(context)
    }
}
