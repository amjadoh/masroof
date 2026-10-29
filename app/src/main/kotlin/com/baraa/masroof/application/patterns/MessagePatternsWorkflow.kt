package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import com.baraa.masroof.engine.bank.patterns.GenericCandidateExtractor
import com.baraa.masroof.engine.bank.patterns.FieldSlot
import com.baraa.masroof.engine.bank.patterns.SavedMessagePattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Teaching stays preview-only; financial approval, import and eligible pending application are explicit actions. */
class MessagePatternsWorkflow(
    private val store: MessagePatternStore,
    private val accessGate: DatabaseAccessGate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pendingPreview: PendingPatternsWorkflow? = null,
    private val bundledBanks: List<BankMessagePatterns> = emptyList(),
    private val activation: PatternApprovalWorkflow? = null,
    private val importBank: (suspend (String) -> com.baraa.masroof.application.sms.SmsScanResult)? = null,
    private val requiredBundledBankIds: Set<String> = emptySet(),
) {
    val canPreviewPending: Boolean get() = pendingPreview != null
    val canApplyPending: Boolean get() = pendingPreview?.canApply == true
    val canApprove: Boolean get() = activation != null
    val canImportBank: Boolean get() = importBank != null
    suspend fun importBank(bankId: String) = requireNotNull(importBank).invoke(bankId)
    suspend fun patternCards(bankId: String) = requireNotNull(activation).cards(bankId)
    suspend fun patternAccounts(bankId: String) = requireNotNull(activation).accounts(bankId)
    suspend fun confirmPatternAccount(bankId: String, last4: String) = requireNotNull(activation).confirmAccount(bankId, last4)
    suspend fun bankAccountSetup(bankId: String) = requireNotNull(activation).bankAccountSetup(bankId)
    suspend fun configureBankAccount(generation: Long, bankId: String, accountId: String, currency: String, personalLoan: Boolean): PatternCatalog {
        requireNotNull(activation).configureBankAccount(generation, bankId, accountId,
            com.baraa.masroof.finance.value.Currency.valueOf(currency), personalLoan)
        val current = load()
        val processor = pendingPreview?.takeIf { it.canApply }
        if (processor != null) {
            do {
                val report = processor.apply(bankId, current.generation)
                check(!report.incomplete) { "Account saved; pending processing is incomplete and retryable" }
                val remaining = processor.preview(bankId, current.generation)
                if (!remaining.limited || report.processed == 0) break
            } while (true)
        }
        return current.toCatalog()
    }
    suspend fun previewFinancial(generation: Long, bankId: String, patternId: String, operation: PatternOperation, accountId: String?,
        body: String, referenceField: String?, contextReferences: Set<String>, currency: com.baraa.masroof.core.money.Currency? = null, cardId: String? = null) =
        requireNotNull(activation).preview(generation, bankId, patternId, operation, accountId, body, referenceField, contextReferences, currency?.let { com.baraa.masroof.finance.value.Currency.valueOf(it.name) }, cardId)
    suspend fun saveFinancial(preview: PatternFinancialPreview) = requireNotNull(activation).save(preview)
    suspend fun applyPending(bankId: String, generation: Long, reviewId: String? = null, patternId: String? = null) =
        requireNotNull(pendingPreview).apply(bankId, generation, reviewId, patternId)
    suspend fun approvalExamples(bankId: String, generation: Long, patternId: String) =
        pendingPreview?.approvalExamples(bankId, generation, patternId).orEmpty()

    suspend fun previewPending(bankId: String, generation: Long) =
        requireNotNull(pendingPreview) { "Pending preview is unavailable" }.preview(bankId, generation)

    suspend fun catalog(): PatternCatalog = load().toCatalog()

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
                bank.patterns.any { seed -> seed.id !in prior.deletedPatternIds && prior.patterns.none { it.id == seed.id } } || bank.senders.any { it !in prior.senders }
            else configuration.banks.none { it.bankId == bank.bankId || it.senders.any(bank.senders::contains) }
        }

    suspend fun addBank(generation: Long, name: String, sender: String, bankId: String? = null) = withContext(ioDispatcher) {
        accessGate.withRestore {
            val current = store.load()
            val id = bankId ?: "BANK_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            require(current.banks.none { it.bankId == id }) { "Bank already configured" }
            validateBankEdit(current.banks, id, name, listOf(sender.trim()))
            store.saveBank(generation, BankMessagePatterns(id, listOf(sender.trim()), name.trim())).toCatalog()
        }
    }

    suspend fun updateBank(generation: Long, bankId: String, name: String, senders: List<String>) = withContext(ioDispatcher) {
        accessGate.withRestore {
            val current = store.load()
            val bank = current.banks.single { it.bankId == bankId }
            val addresses = senders.map(String::trim).distinct()
            validateBankEdit(current.banks, bankId, name, addresses)
            require(bank.currentPatterns.all { it.sender in addresses }) { "A sender is still used by a pattern" }
            store.saveBank(generation, bank.copy(displayName = name.trim(), senders = addresses, patterns = emptyList())).toCatalog()
        }
    }

    private fun validateBankEdit(banks: List<BankMessagePatterns>, bankId: String, name: String, senders: List<String>) {
        require(name.trim().isNotEmpty() && name.length <= 120)
        require(senders.isNotEmpty() && senders.size <= 32 && senders.all { it.isNotBlank() && it.length <= 120 })
        val existing = banks.singleOrNull { it.bankId == bankId }?.senders.orEmpty()
        require(senders.filterNot { it in existing }.none { sender -> banks.any { it.bankId != bankId && sender in it.senders } }) {
            "Sender belongs to another bank"
        }
    }

    suspend fun candidates(body: String) = withContext(ioDispatcher) {
        GenericCandidateExtractor.extract(body).map { PatternText(it.text.raw, it.text.start, it.text.end) }.distinct()
    }

    suspend fun editBindings(generation: Long, bankId: String, patternId: String, body: String) = withContext(ioDispatcher) {
        accessGate.withAccess {
            val configuration = store.load()
            check(configuration.generation == generation) { "Pattern configuration changed" }
            val pattern = configuration.banks.single { it.bankId == bankId }.currentPatterns.single { it.id == patternId }
            // Role evidence may include labels and multiple observations; it is not a set of teaching selections.
            if (pattern.roleProfile != null) return@withAccess emptyList<PatternBinding>()
            val ignored = pattern.parts.filterIsInstance<FieldSlot>().filter { it.ignored }.map { it.field }.toSet()
            val visible = pattern.copy(enabled = true, parts = pattern.parts.map {
                if (it is FieldSlot) it.copy(ignored = false) else it
            })
            visible.preview(bankId, pattern.sender, body)?.fields.orEmpty().map { (field, span) ->
                PatternBinding(field, span.start, span.end, ignored = field in ignored)
            }
        }
    }

    suspend fun teach(expectedGeneration: Long, bankId: String, sender: String, operation: PatternOperation,
        body: String, bindings: List<PatternBinding>, patternId: String? = null): PatternTeachingPreview = withContext(ioDispatcher) {
        accessGate.withAccess {
            val configuration = store.load()
            check(configuration.generation == expectedGeneration) { "Pattern configuration changed" }
            val bank = configuration.banks.single { it.bankId == bankId }
            val prior = patternId?.let { id -> bank.currentPatterns.single { it.id == id } }
            require(sender in bank.senders) { "Select a configured sender" }
            require(bindings.any { it.field == "amount" && !it.ignored }) { "Bind the transaction amount" }
            require(bindings.all { it.ignored || it.field in operation.fields }) { "Field does not belong to operation" }
            val pattern = SavedMessagePattern.fromChoices(prior?.id ?: java.util.UUID.randomUUID().toString(), bankId, sender,
                operation.name, body, bindings.choices()).copy(revision = prior?.let { Math.addExact(it.revision, 1) } ?: 1,
                    enabled = prior?.enabled ?: true)
            PatternTeachingPreview(configuration.generation, pattern,
                requireNotNull(pattern.copy(enabled = true).preview(bankId, sender, body)).fields.displayFields())
        }
    }

    suspend fun tryPreview(preview: PatternTeachingPreview, sender: String, body: String) = withContext(ioDispatcher) {
        preview.pattern.copy(enabled = true).preview(preview.bankId, sender, body)?.fields?.displayFields()
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

    suspend fun deletePattern(generation: Long, bankId: String, patternId: String) = withContext(ioDispatcher) {
        accessGate.withRestore { store.deletePattern(generation, bankId, patternId).toCatalog() }
    }

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
