package com.baraa.masroof.application.patterns

import android.content.Context
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.lab.bank.patterns.BankMessagePatterns
import com.baraa.masroof.lab.bank.patterns.GenericCandidateExtractor
import com.baraa.masroof.lab.bank.patterns.SavedMessagePattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Configuration only. No routing, reprocessing, ownership or ledger writes are activated here. */
class MessagePatternsWorkflow(
    private val store: MessagePatternStore,
    private val accessGate: DatabaseAccessGate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load() = withContext(ioDispatcher) { accessGate.withAccess { store.load() } }

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

    /** All matches are shown: no first-match winner hides an ambiguous definition. */
    suspend fun match(bankId: String, sender: String, body: String) = withContext(ioDispatcher) {
        accessGate.withAccess {
            store.load().banks.singleOrNull { it.bankId == bankId }?.currentPatterns.orEmpty().mapNotNull { pattern ->
                pattern.preview(bankId, sender, body)?.let {
                    PatternMatch(pattern.id, pattern.revision, pattern.label, it.fields.displayFields())
                }
            }
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
