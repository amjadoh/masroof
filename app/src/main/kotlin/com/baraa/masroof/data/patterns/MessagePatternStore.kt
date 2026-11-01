package com.baraa.masroof.data.patterns

import android.content.Context
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration
import com.baraa.masroof.engine.bank.patterns.SavedMessagePattern
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Durable user definitions. Reuse decoding only while the complete persisted text is unchanged. */
class MessagePatternStore(private val file: File, private val beforeReplace: () -> Unit = {}) {
    constructor(context: Context) : this(File(context.filesDir, "message-patterns/definitions.json"))

    private var decodedRaw: String? = null
    private var decoded: MessagePatternConfiguration? = null

    fun load(): MessagePatternConfiguration = synchronized(lock) {
        val raw = captureRaw()
        // Generation and file timestamps alone cannot distinguish restores or external replacements.
        if (raw == decodedRaw) decoded?.let { return@synchronized it }
        val configuration = raw?.let(MessagePatternConfigurationCodec::decode) ?: MessagePatternConfiguration()
        decodedRaw = raw
        decoded = configuration
        configuration
    }

    /** Startup-only before this container admits work; interactive installation uses the exclusive access gate. */
    fun installMissingBanks(bundled: List<BankMessagePatterns>, mergeBankIds: Set<String> = emptySet()): MessagePatternConfiguration = synchronized(lock) {
        var current = load()
        // Installed role rules are never replaced at startup, so an app update cannot change how saved banks read messages.
        for (bank in bundled) installation(current, bank, mergeBankIds, upgradeRoles = false)?.let { current = write(current, it) }
        current
    }

    fun saveBank(expectedGeneration: Long, bank: BankMessagePatterns): MessagePatternConfiguration = synchronized(lock) {
        val current = load()
        check(current.generation == expectedGeneration) { "Pattern configuration changed" }
        val prior = current.banks.singleOrNull { it.bankId == bank.bankId }
        val deletedIds = prior?.deletedPatternIds.orEmpty() + bank.deletedPatternIds
        require(bank.patterns.none { it.id in deletedIds }) { "Pattern was permanently deleted" }
        val versions = prior?.patterns.orEmpty().associateBy { it.id to it.revision }.toMutableMap()
        bank.patterns.sortedBy { it.revision }.forEach { pattern ->
            val key = pattern.id to pattern.revision
            val existing = versions[key]
            if (existing != null) require(existing == pattern) { "Pattern revision is immutable" }
            else {
                require(pattern.revision > (versions.values.filter { it.id == pattern.id }.maxOfOrNull { it.revision } ?: 0)) {
                    "Pattern revision must advance"
                }
                versions[key] = pattern
            }
        }
        val updated = bank.copy(patterns = keepRecent(versions.values), deletedPatternIds = deletedIds)
        val next = MessagePatternConfiguration(Math.addExact(current.generation, 1),
            current.banks.filterNot { it.bankId == bank.bankId } + updated, current.confirmedLoanPayments)
        val raw = MessagePatternConfigurationCodec.encode(next)
        replace(raw)
        MessagePatternConfigurationCodec.decode(raw)
    }

    fun deletePattern(expectedGeneration: Long, bankId: String, patternId: String): MessagePatternConfiguration = synchronized(lock) {
        val current = load()
        check(current.generation == expectedGeneration) { "Pattern configuration changed" }
        val bank = current.banks.single { it.bankId == bankId }
        val pattern = bank.currentPatterns.single { it.id == patternId }
        require(!pattern.enabled) { "Disable the pattern before deleting it" }
        write(current, bank.copy(patterns = bank.patterns.filterNot { it.id == patternId },
            deletedPatternIds = bank.deletedPatternIds + patternId))
    }

    /** Replaces one bank's stored history as given; callers own its revision rules. */
    private fun write(current: MessagePatternConfiguration, updated: BankMessagePatterns): MessagePatternConfiguration {
        val next = current.copy(generation = Math.addExact(current.generation, 1),
            banks = current.banks.filterNot { it.bankId == updated.bankId } + updated)
        val raw = MessagePatternConfigurationCodec.encode(next)
        replace(raw)
        return MessagePatternConfigurationCodec.decode(raw)
    }

    fun saveConfirmedLoanPayments(expectedGeneration: Long,
        rules: List<com.baraa.masroof.engine.bank.patterns.ConfirmedLoanPayment>): MessagePatternConfiguration = synchronized(lock) {
        val current = load()
        check(current.generation == expectedGeneration) { "Configuration changed" }
        val next = current.copy(generation = Math.addExact(current.generation, 1), confirmedLoanPayments = rules)
        val raw = MessagePatternConfigurationCodec.encode(next)
        replace(raw)
        MessagePatternConfigurationCodec.decode(raw)
    }

    /** Bounded raw capture lets restore recovery preserve even a corrupt original without executing it. */
    fun captureRaw(): String? = synchronized(lock) {
        if (!file.exists()) return@synchronized null
        require(file.isFile && file.length() <= MessagePatternConfigurationCodec.MAX_BYTES) { "Invalid pattern configuration file" }
        file.readText(Charsets.UTF_8)
    }

    /** Validation may be skipped ONLY when rollback restores the trusted original journal snapshot. */
    fun restoreRaw(raw: String?, validate: Boolean = true): Unit = synchronized(lock) {
        if (raw != null) {
            require(raw.toByteArray(Charsets.UTF_8).size <= MessagePatternConfigurationCodec.MAX_BYTES)
            if (validate) MessagePatternConfigurationCodec.decode(raw)
            replace(raw)
        } else if (file.exists() && (!file.isFile || !file.delete())) {
            throw IOException("Cannot clear pattern configuration")
        }
    }

    private fun replace(raw: String) {
        val target = file.absoluteFile
        val directory = requireNotNull(target.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create pattern directory" }
        val temporary = Files.createTempFile(directory.toPath(), ".patterns-", ".new")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(raw.toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            beforeReplace()
            Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        /**
         * The bank to store for one bundled definition, or null when nothing changes. A missing bank installs unless
         * another bank owns its sender; an installed bank gains missing patterns and newer bundled role rules the user
         * left untouched (deleted, disabled and edited patterns stay the user's decision). [mergeBankIds] also merge senders.
         */
        fun installation(configuration: MessagePatternConfiguration, seed: BankMessagePatterns,
            mergeBankIds: Set<String>, upgradeRoles: Boolean = true): BankMessagePatterns? {
            val prior = configuration.banks.singleOrNull { it.bankId == seed.bankId }
                ?: return seed.takeIf { seed.bankId in mergeBankIds || configuration.banks.none { it.senders.any(seed.senders::contains) } }
            val installed = prior.currentPatterns.associateBy { it.id }
            // Bundled files may carry earlier revisions; only each rule's current revision is a candidate.
            val updates = seed.currentPatterns.mapNotNull { bundled ->
                val mine = installed[bundled.id]
                when {
                    bundled.id in prior.deletedPatternIds -> null
                    mine == null -> bundled
                    // A confirmed bank setup (a contract with its own currency) is the user's decision.
                    upgradeRoles && mine.factContract?.currency == null &&
                        mine.roleProfile != null && bundled.roleProfile != null && mine.enabled &&
                        mine.copy(revision = bundled.revision, factContract = mine.factContract?.unlinked()) != bundled -> {
                        // Approved account/card links travel with a newer rule of the same family; a changed family needs a fresh approval.
                        val links = mine.factContract?.accountBindings.orEmpty()
                        if (links.isNotEmpty() && mine.factContract?.family != bundled.factContract?.family) null
                        else bundled.copy(revision = Math.addExact(mine.revision, 1), factContract = bundled.factContract?.let {
                            if (links.isEmpty()) it else it.copy(version = 2, accountBindings = links) })
                    }
                    else -> null
                }
            }
            val senders = if (seed.bankId in mergeBankIds) (prior.senders + seed.senders).distinct() else prior.senders
            if (updates.isEmpty() && senders == prior.senders) return null
            // An upgraded role keeps only its new revision, so repeated updates never grow the bounded history.
            val upgraded = updates.map { it.id }.toSet()
            return prior.copy(senders = senders,
                patterns = keepRecent(prior.patterns.filterNot { it.id in upgraded && it.roleProfile != null } + updates))
        }

        private fun com.baraa.masroof.engine.bank.patterns.PatternFactContract.unlinked() = copy(version = 1, accountBindings = emptyList())

        /** Every save adds a revision; keep the newest few per pattern so a bank never reaches the codec's 128 cap. */
        internal fun keepRecent(patterns: Collection<SavedMessagePattern>): List<SavedMessagePattern> =
            patterns.groupBy { it.id }.values.flatMap { history -> history.sortedByDescending { it.revision }.take(KEPT_REVISIONS) }

        internal const val KEPT_REVISIONS = 4

        // ponytail: serialize local file operations process-wide; split by path only if multiple profiles need concurrent writes.
        private val lock = Any()
    }
}
