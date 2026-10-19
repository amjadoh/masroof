package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.BetaDatabase
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.PostingKey
import com.baraa.masroof.lab.finance.value.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class BetaInterpretationUpgradeTest {
    @Test fun existingBankEngineEvidenceIsReinterpretedWithoutResetOrRepeatedOwnership() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val bank=BankId("BANK_ALJAZIRA")
        try {
            val id=BetaContainer(context).use {beta ->
                beta.acknowledgeStart();beta.setBankEnabled(bank,true)
                beta.registerAsset(OwnedAsset(AssetId(Environment.BETA,bank,"upgrade-account"),AssetKind.ACCOUNT,"1234"))
                (beta.importEvidence(EvidenceRequest("upgrade",EvidenceOrigin.EXPLICIT_HISTORY,setOf(bank)),listOf(EvidenceMessage("old","AlJazira",
                    "Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR",Instant.parse("2026-10-18T12:00:00Z"),ReceiptTimeSource.PROVIDER_TIMESTAMP))).single() as EvidenceResult.Stored).summary.id
            }
            val db=Room.databaseBuilder(context,BetaDatabase::class.java,BetaDatabase.NAME).build()
            try {runBlocking {
                val outcome=requireNotNull(db.evidenceDao().outcome(id))
                val old=JSONObject(outcome.candidateJson).put("engineVersion","rule-interpreter-v7").put("status","REVIEW")
                db.openHelper.writableDatabase.execSQL("UPDATE beta_parsed_event SET candidateJson=? WHERE rawId=?",arrayOf(old.toString(),id))
            }} finally {db.close()}
            context.getSharedPreferences(BetaEnvironment.PREFS_NAME,0).edit().putString("beta_auto_complete_v1","old-engine").commit()
            BetaContainer(context).use {beta ->
                beta.processAutomaticEvidence()
                assertNotNull(beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id")))
                assertEquals(1,beta.assets().size)
                assertEquals(0,beta.evidenceInboxCounts().needsReview)
                val posting=beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id"))
                beta.processAutomaticEvidence()
                assertEquals(posting,beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id")))
            }
        } finally {BetaContainer.resetOffline(context)}
    }
}
