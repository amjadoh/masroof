package com.baraa.masroof.lab.replica.application.interpretation

import android.content.Context
import androidx.room.InvalidationTracker
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.*
import com.baraa.masroof.lab.bank.learning.*
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.replica.application.logging.AppLogService
import com.baraa.masroof.lab.replica.data.learning.*
import com.baraa.masroof.lab.replica.domain.repository.*
import com.baraa.masroof.lab.replica.parsing.repository.ParsedEventRepository
import com.baraa.masroof.lab.replica.domain.model.ReviewResolutionKind
import com.baraa.masroof.lab.replica.domain.model.ReviewStatus
import com.baraa.masroof.lab.replica.domain.model.FinancialTransactionType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** ML is a family assistant; the production workflows retain all financial authority. */
class LabInterpretationService(
    private val context: Context,
    private val raw: RawSmsRepository,
    private val parsed: ParsedEventRepository,
    private val corrections: UserCorrectionRepository,
    private val reviews: ReviewRepository,
    private val transactions: FinancialTransactionRepository,
    private val scope: CoroutineScope,
    private val log: AppLogService,
) : AutoCloseable {
    private val store = LabLearningStore(context)
    private val seed = runCatching {store.loadSeed()}.getOrElse { log.warn("ML","Seed unavailable: ${it.javaClass.simpleName}");null }
    @Volatile private var classifier = seed?.let { model ->
        val labels = runCatching {store.cached(model)}.getOrElse {log.warn("ML","Cached model rejected: ${it.javaClass.simpleName}");emptyList()}
        FamilyModelCodec.checked(model,labels)
    }
    private val engine = RuleInterpreter(AlJaziraRuleProfile.current(),FamilyAssist { input,title -> classifier?.suggest(BANK,input.body,title) })
    private val mutex = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var tracker: InvalidationTracker? = null
    private val observer = object : InvalidationTracker.Observer("user_correction","review_item") {
        override fun onInvalidated(tables: Set<String>) { changes.trySend(Unit) }
    }
    val modelVersion: String? get() = classifier?.version
    fun interpret(input: MessageInput): Interpretation {
        val activeModel = classifier?.version
        val result = engine.interpretRouted(input,BankId(BANK))
        // Advisory audit writes never decide whether a transaction may be posted.
        scope.launch {
            try {
                val key=FamilyFeatures.sha256("${input.evidenceId}|${result.version}|${result.familyInference?.modelVersion?:activeModel}")
                store.record(EngineInterpretationRow(key,input.evidenceId,result.bank?.value,result.version.engine,result.version.rules,
                    result.familyInference?.modelVersion?:activeModel,result.familyInference?.origin?.name?:"RULES",result.facts.family.name,result.status.name,
                    result.blockers.joinToString(",") {it.name},FamilyFeatures.sha256(input.body),input.receivedAt.toEpochMilli(),System.currentTimeMillis()))
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { log.warn("ML","Interpretation audit pending: ${e.javaClass.simpleName}") }
        }
        return result
    }
    @Synchronized fun startLearning(invalidationTracker: InvalidationTracker) {
        if (job!=null) return
        tracker=invalidationTracker;invalidationTracker.addObserver(observer)
        job=scope.launch {
            for (ignored in changes) {
                try { synchronizeFeedback() }
                catch (e: CancellationException) {throw e}
                catch (e: Exception) {log.warn("ML","Feedback sync pending: ${e.javaClass.simpleName}")}
            }
        }
        changes.trySend(Unit)
    }
    suspend fun synchronizeFeedback(): String? = mutex.withLock {
        val seed=seed?:return@withLock null
        val decisions=mutableListOf<Decision>()
        val ids=raw.listIdsByReceivedAt()
        corrections.listForRawSmsIds(ids).forEach { correction ->
            correction.correctedType?.takeIf {it.name!="UNKNOWN"}?.let {family ->
                decisions += Decision("correction:${correction.id}",correction.targetRawSmsId,MessageFamily.valueOf(family.name),correction.createdAt.toEpochMilli(),"USER_MESSAGE_FAMILY")
            }
        }
        val reviewRows=reviews.listAll()
        for (review in reviewRows.filter {it.status==ReviewStatus.RESOLVED && it.resolutionKind==ReviewResolutionKind.USER_FINANCIAL_TYPE}) {
            val transaction=review.resolvedTransactionId?.let {transactions.getById(it)}?:continue
            val family=eventKindFamily(transaction.type)?:continue
            val at=review.resolvedAt?.toEpochMilli()?:continue
            decisions += Decision("kind:${review.id}:$at",review.rawSmsId,family,at,"USER_EVENT_KIND")
        }
        val wanted=decisions.map {it.rawId}.toSet()
        val evidence=raw.getByIds(wanted).associateBy {it.id}
        val events=parsed.listByRawSmsIds(wanted).associateBy {it.event.rawSmsId}
        val labels=decisions.mapNotNull { decision ->
            val message=evidence[decision.rawId]?:return@mapNotNull null
            val bank=events[decision.rawId]?.event?.bank?.id?:return@mapNotNull null
            if (bank!=BANK) return@mapNotNull null
            val features=FamilyFeatures.extract(message.body)
            LearningLabelRow(decision.id,decision.rawId,bank,decision.family.name,features.template,FamilyModelCodec.termsJson(features.terms).toString(),
                FamilyFeatures.VERSION,decision.at,decision.provenance,message.bodyHash)
        }
        store.saveLabels(labels)
        val learned=store.labels()
        val updated=FamilyModelCodec.checked(seed,learned)
        val changed=classifier?.version!=updated.version
        store.persist(seed,learned,updated)
        classifier=updated
        val prefs=context.getSharedPreferences("learning_runtime",Context.MODE_PRIVATE)
        if (changed || prefs.getString("scheduled_model",null)!=updated.version) {
            val pending=reviewRows.any {it.status==ReviewStatus.REQUIRED && parsed.findByRawSmsId(it.rawSmsId)?.event?.messageFamily?.name=="UNKNOWN"}
            if (pending) ModelRefreshWorker.enqueue(context,updated.version)
            check(prefs.edit().putString("scheduled_model",updated.version).commit())
        }
        updated.version
    }
    private data class Decision(val id: String,val rawId: String,val family: MessageFamily,val at: Long,val provenance: String)
    private fun eventKindFamily(type: FinancialTransactionType): MessageFamily? = when(type) {
        FinancialTransactionType.INCOME,FinancialTransactionType.EXTERNAL_TRANSFER_IN -> MessageFamily.TRANSFER_IN
        FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> MessageFamily.TRANSFER_OUT
        FinancialTransactionType.CREDIT_CARD_PAYMENT -> MessageFamily.CARD_PAYMENT
        FinancialTransactionType.REFUND -> MessageFamily.REFUND
        FinancialTransactionType.CASH_WITHDRAWAL -> MessageFamily.WITHDRAWAL
        FinancialTransactionType.BILL_PAYMENT -> MessageFamily.BILL_PAYMENT
        FinancialTransactionType.LOAN_REPAYMENT -> MessageFamily.FINANCING_INSTALLMENT
        FinancialTransactionType.FEE -> MessageFamily.FEE
        else -> null // "Expense"/self-transfer/category changes do not identify a message family.
    }
    suspend fun auditHistory(rawId: String) = store.history(rawId)
    fun invalidateForRestore() {
        close()
        store.invalidate()
        context.getSharedPreferences("learning_runtime",Context.MODE_PRIVATE).edit().clear().commit()
    }
    override fun close() {
        tracker?.removeObserver(observer);tracker=null
        runBlocking {job?.cancelAndJoin()};job=null
        store.close()
    }
    companion object { const val BANK="BANK_ALJAZIRA" }
}
