package com.baraa.masroof.application.patterns

import android.content.Context
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.lab.bank.patterns.BankMessagePatterns
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
