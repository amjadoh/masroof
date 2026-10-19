package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.PostingKey
import com.baraa.masroof.lab.finance.value.*
import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Checks the entire persisted intake/ownership/adoption path, not only parser predictions. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class BetaKnownCorpusLifecycleTest {
    @Test fun completeKnownFinancialFixturesDoNotNeedRepeatedMessageDefinition() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val bank=BankId("BANK_ALJAZIRA")
        val results=mutableListOf<String>()
        val failures=mutableListOf<String>()
        File("../../app/src/test/resources/testdata/bank_aljazira").walkTopDown()
            .filter {it.extension=="json" && it.parentFile?.name!="reference"}.sortedBy {it.name}.forEach {file ->
                val sample=JSONObject(file.readText())
                if(sample.getJSONObject("expected").optString("parseStatus")!="SUCCESS") return@forEach
                BetaContainer.resetOffline(context)
                try {BetaContainer(context).use {beta ->
                    beta.acknowledgeStart();beta.setBankEnabled(bank,true)
                    val id=(beta.importEvidence(EvidenceRequest("corpus",EvidenceOrigin.EXPLICIT_HISTORY,setOf(bank)),listOf(
                        EvidenceMessage(sample.getString("id"),sample.getString("sender"),sample.getString("body"),Instant.parse("2026-10-18T00:00:00Z"),ReceiptTimeSource.PROVIDER_TIMESTAMP))).single() as EvidenceResult.Stored).summary.id
                    beta.ownershipSnapshot().candidates.forEach {beta.decideOwnership(it,true)}
                    beta.processAutomaticEvidence()
                    // The owner explicitly selects a kind for an unlabeled card once.
                    beta.ownershipSnapshot().candidates.filter {it.asset==null && it.reference.kind==com.baraa.masroof.lab.beta.application.BetaOwnershipKind.CARD}.forEach {candidate ->
                        beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA,bank,"chosen-card"),AssetKind.CREDIT_CARD,candidate.reference.suffix,
                            parentId=AssetId(Environment.BETA,bank,"chosen-facility"),role=CreditCardRole.PRIMARY,ownedConfirmed=true,createCreditFacility=true))
                    }
                    beta.assets().filter {it.kind==AssetKind.DEBIT_CARD && it.parentId==null}.forEach {card ->
                        // A card-only notification cannot identify its linked account: explicit setup choice.
                        val account=beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA,bank,"chosen-account"),AssetKind.ACCOUNT,"9001",ownedConfirmed=true))
                        beta.saveManualAsset(ManualAssetDraft(card.id,card.kind,card.last4.orEmpty(),parentId=account.id,ownedConfirmed=true,editing=true,expectedOriginal=card))
                    }
                    beta.assets().filter {it.kind==AssetKind.CREDIT_CARD && it.parentId==null}.forEach {card ->
                        beta.saveManualAsset(ManualAssetDraft(card.id,card.kind,card.last4.orEmpty(),parentId=AssetId(Environment.BETA,bank,"facility-${card.id.localId}"),role=CreditCardRole.PRIMARY,
                            ownedConfirmed=true,editing=true,expectedOriginal=card,createCreditFacility=true))
                    }
                    beta.processAutomaticEvidence()
                    val summary=beta.evidenceSummary(id)
                    val posted=beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id"))!=null
                    val reason=summary.financialReviewReason
                    results+="${file.name}: posted=$posted; reason=$reason"
                    if(!posted) failures+=results.last()
                }} finally {BetaContainer.resetOffline(context)}
            }
        File("build/reports/beta-interpretation/known-corpus-lifecycle.txt").apply {requireNotNull(parentFile).mkdirs();writeText(results.joinToString("\n"))}
        assertTrue("Known lifecycle failures:\n${failures.joinToString("\n")}",failures.isEmpty())
        assertEquals(27,results.size)
    }
}
