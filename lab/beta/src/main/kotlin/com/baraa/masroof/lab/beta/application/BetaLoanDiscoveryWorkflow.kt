package com.baraa.masroof.lab.beta.application

import androidx.room.withTransaction
import com.baraa.masroof.lab.beta.application.evidence.EvidenceDisposition
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import java.util.UUID

/** Same ownership rule as production: a validated installment from an owned account
 * establishes the bank/type loan. It never infers account ownership or a loan type. */
class BetaLoanDiscoveryWorkflow(private val database: BetaDatabase) {
    suspend fun synchronize(ids: List<String>) = database.withTransaction {
        val enabled=database.bankSelectionDao().all().filter {it.state=="ENABLED"}.map {it.bankId}.toSet()
        val registry=BetaAssetRegistry(database)
        val accounts=registry.owned().filter {it.kind==AssetKind.ACCOUNT}
        val store=RoomBetaEvidenceStore(database)
        for(id in ids) {
            val dao=database.evidenceDao()
            if(dao.retry(id)!=null || database.financialAdoptionDao().retry(id)!=null) continue
            if(dao.decision(id)?.decision.let {it!=null && it!="RESTORE_AUTOMATIC"}) continue
            val outcome=dao.outcome(id)?.takeIf {it.disposition==EvidenceDisposition.INTERPRETED.name} ?: continue
            val raw=dao.raw(id) ?: continue
            val archive=runCatching {BetaCurrentFinancialArchive.read(outcome,raw.body,durableReceipt=raw.durableReceiptTime())}.getOrNull() ?: continue
            if(archive.optString("status")!="SUCCESS") continue
            val bank=archive.getString("bank")
            if(bank !in enabled) continue
            val facts=archive.getJSONObject("facts")
            if(facts.optString("family")!="FINANCING_INSTALLMENT" || facts.optString("direction")!="OUTGOING") continue
            val type=facts.optString("loanType").takeIf {it in setOf("PERSONAL","AUTO","MORTGAGE")} ?: continue
            val source=facts.optJSONObject("sourceAccount") ?: continue
            if(source.optString("bank")!=bank) continue
            val suffix=source.optString("number")
            val proposals=store.ownershipObservations(id).mapNotNull {it.proposal}
            if(proposals.none {it.kind==AssetKind.ACCOUNT && it.id.bank.value==bank && it.last4==suffix}) continue
            val decision=database.ownershipDao().all().singleOrNull {it.bankId==bank && it.kind=="ACCOUNT" && it.suffix==suffix}
            if(decision?.status in setOf("EXTERNAL","REVIEW")) continue
            val candidates=accounts.filter {it.id.bank.value==bank && it.last4==suffix}
            val sourceOwned=if(decision?.assetLocalId!=null) candidates.singleOrNull {it.id.localId==decision.assetLocalId} else candidates.singleOrNull()
            if(sourceOwned==null) continue
            val loans=database.assetDao().all().filter {it.bankId==bank && it.kind=="LOAN"}
            // Never replace an explicit registry identity or guess which untyped loan it is.
            if(loans.any {it.loanType==type || it.loanType==null}) continue
            val localId=UUID.nameUUIDFromBytes("beta-loan|$bank|$type".toByteArray(Charsets.UTF_8)).toString()
            if(loans.any {it.localId==localId}) continue
            registry.register(OwnedAsset(AssetId(Environment.BETA,BankId(bank),localId),AssetKind.LOAN))
            check(database.assetDao().metadataLoan(Environment.BETA.name,bank,localId,null,null,null,type,0)==1)
        }
    }
}
