package com.baraa.masroof.application.patterns

import android.content.Context
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import com.baraa.masroof.engine.bank.patterns.GenericCandidateExtractor
import com.baraa.masroof.engine.bank.patterns.SavedMessagePattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Teaching stays preview-only; financial approval, import and eligible pending application are explicit actions. */
class MessagePatternsWorkflow(
    private val store: MessagePatternStore,
    private val accessGate: DatabaseAccessGate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pendingPreview: PendingPatternPreviewWorkflow? = null,
    private val bundledBanks: List<BankMessagePatterns> = emptyList(),
    private val activation: PatternActivationWorkflow? = null,
    private val importBank: (suspend (String) -> com.baraa.masroof.application.sms.SmsScanResult)? = null,
    private val requiredBundledBankIds: Set<String> = emptySet(),
) {
    val canPreviewPending: Boolean get() = pendingPreview != null
    val canApplyPending: Boolean get() = pendingPreview?.canApply == true
    val canApprove: Boolean get() = activation != null
    val canImportBank: Boolean get() = importBank != null
    suspend fun importBank(bankId: String) = requireNotNull(importBank).invoke(bankId)
    suspend fun patternAccounts(bankId: String) = requireNotNull(activation).accounts(bankId)
    suspend fun confirmPatternAccount(bankId: String, last4: String) = requireNotNull(activation).confirmAccount(bankId, last4)
    suspend fun previewFinancial(generation: Long, bankId: String, patternId: String, operation: PatternOperation, accountId: String,
        body: String, referenceField: String?, contextReferences: Set<String>, currency: com.baraa.masroof.finance.value.Currency? = null) =
        requireNotNull(activation).preview(generation, bankId, patternId, operation, accountId, body, referenceField, contextReferences, currency)
    suspend fun saveFinancial(preview: PatternFinancialPreview) = requireNotNull(activation).save(preview)
    suspend fun applyPending(bankId: String, generation: Long) = requireNotNull(pendingPreview).apply(bankId, generation)

    suspend fun previewPending(bankId: String, generation: Long) =
        requireNotNull(pendingPreview) { "Pending preview is unavailable" }.preview(bankId, generation)

    suspend fun load() = withContext(ioDispatcher) {
        val existing = accessGate.withAccess { store.load() }
        if (missingBanks(existing).isEmpty()) existing
        else accessGate.withRestore {
            store.installMissingBanks(bundledBanks, requiredBundledBankIds)
        }
    }

    private fun missingBanks(configuration: com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration) =
        bundledBanks.filter { bank ->
            val prior = configuration.banks.singleOrNull { it.bankId == bank.bankId }
            if (bank.bankId in requiredBundledBankIds) prior == null ||
                bank.patterns.any { seed -> prior.patterns.none { it.id == seed.id } } || bank.senders.any { it !in prior.senders }
            else configuration.banks.none { it.bankId == bank.bankId || it.senders.any(bank.senders::contains) }
        }

    suspend fun addBank(generation: Long, name: String, sender: String, bankId: String? = null) = withContext(ioDispatcher) {
        accessGate.withRestore {
            val current = store.load()
            val id = bankId ?: "BANK_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            require(current.banks.none { it.bankId == id }) { "Bank already configured" }
            store.saveBank(generation, BankMessagePatterns(id, listOf(sender.trim()), name.trim()))
        }
    }

    suspend fun candidates(body: String) = withContext(ioDispatcher) {
        GenericCandidateExtractor.extract(body).map { PatternText(it.text.raw, it.text.start, it.text.end) }.distinct()
    }

    suspend fun teach(expectedGeneration: Long, bankId: String, sender: String, operation: PatternOperation,
        body: String, bindings: List<PatternBinding>): PatternTeachingPreview = withContext(ioDispatcher) {
        accessGate.withAccess {
            val configuration = store.load()
            check(configuration.generation == expectedGeneration) { "Pattern configuration changed" }
            val bank = configuration.banks.single { it.bankId == bankId }
            require(sender in bank.senders) { "Select a configured sender" }
            require(bindings.any { it.field == "amount" && !it.ignored }) { "Bind the transaction amount" }
            require(bindings.all { it.ignored || it.field in operation.fields }) { "Field does not belong to operation" }
            val pattern = SavedMessagePattern.fromChoices(java.util.UUID.randomUUID().toString(), bankId, sender,
                operation.name, body, bindings.choices())
            PatternTeachingPreview(configuration.generation, pattern,
                requireNotNull(pattern.preview(bankId, sender, body)).fields.displayFields())
        }
    }

    suspend fun tryPreview(preview: PatternTeachingPreview, sender: String, body: String) = withContext(ioDispatcher) {
        preview.pattern.preview(preview.bankId, sender, body)?.fields?.displayFields()
    }

    suspend fun saveTeaching(preview: PatternTeachingPreview) = withContext(ioDispatcher) {
        accessGate.withRestore {
            val current = store.load()
            check(current.generation == preview.generation) { "Preview expired; review again" }
            val bank = current.banks.single { it.bankId == preview.bankId }
            require(preview.pattern.sender in bank.senders)
            store.saveBank(preview.generation, bank.copy(patterns = listOf(preview.pattern)))
        }
    }

    suspend fun setEnabled(generation: Long, bankId: String, patternId: String, enabled: Boolean) =
        revise(generation, bankId, patternId) { _, current -> current.copy(enabled = enabled) }

    suspend fun restoreRevision(generation: Long, bankId: String, patternId: String, revision: Int) =
        revise(generation, bankId, patternId) { bank, _ -> bank.patterns.single { it.id == patternId && it.revision == revision } }

    private suspend fun revise(generation: Long, bankId: String, patternId: String,
        definition: (BankMessagePatterns, SavedMessagePattern) -> SavedMessagePattern) = withContext(ioDispatcher) {
        accessGate.withRestore {
            val snapshot = store.load()
            check(snapshot.generation == generation) { "Pattern configuration changed" }
            val bank = snapshot.banks.single { it.bankId == bankId }
            val current = bank.currentPatterns.single { it.id == patternId }
            val next = definition(bank, current).copy(revision = Math.addExact(current.revision, 1))
            store.saveBank(generation, bank.copy(patterns = listOf(next)))
        }
    }

    suspend fun saveBank(expectedGeneration: Long, bank: BankMessagePatterns) = withContext(ioDispatcher) {
        // ponytail: reuse the existing exclusive gate for rare edits; no second financial/configuration lock hierarchy.
        accessGate.withRestore { store.saveBank(expectedGeneration, bank) }
    }
}

/** Backup export runs under an access lease; restore/recovery already own exclusive access. */
object MessagePatternBackup {
    fun capture(context: Context): String? = captureOriginal(context).also(::validate)
    fun captureOriginal(context: Context): String? = MessagePatternStore(context).captureRaw()
    fun validate(raw: String?) { if (raw != null) MessagePatternConfigurationCodec.decode(raw) }
    fun restore(context: Context, raw: String?, validate: Boolean = true) = MessagePatternStore(context).restoreRaw(raw, validate)
}
