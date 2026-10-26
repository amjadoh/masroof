package com.baraa.masroof.data.patterns

import android.content.Context
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Durable user definitions, never a rebuildable learning cache. No retained decoded state. */
class MessagePatternStore(private val file: File, private val beforeReplace: () -> Unit = {}) {
    constructor(context: Context) : this(File(context.filesDir, "message-patterns/definitions.json"))

    fun load(): MessagePatternConfiguration = synchronized(lock) {
        captureRaw()?.let(MessagePatternConfigurationCodec::decode) ?: MessagePatternConfiguration()
    }

    fun saveBank(expectedGeneration: Long, bank: BankMessagePatterns): MessagePatternConfiguration = synchronized(lock) {
        val current = load()
        check(current.generation == expectedGeneration) { "Pattern configuration changed" }
        val prior = current.banks.singleOrNull { it.bankId == bank.bankId }
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
        val updated = bank.copy(patterns = versions.values.toList())
        val next = MessagePatternConfiguration(Math.addExact(current.generation, 1),
            current.banks.filterNot { it.bankId == bank.bankId } + updated)
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
        // ponytail: serialize local file operations process-wide; split by path only if multiple profiles need concurrent writes.
        private val lock = Any()
    }
}
