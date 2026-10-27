package com.baraa.masroof.application.patterns

import com.baraa.masroof.engine.bank.patterns.FieldSlot
import com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration
import com.baraa.masroof.engine.bank.patterns.StaticText
import com.baraa.masroof.core.money.Currency

/** Display values only. Approval and posting are rechecked against durable definitions. */
data class PatternCatalog(val generation: Long, val banks: List<PatternBankView>)
data class PatternBankView(val bankId: String, val displayName: String?, val senders: List<String>,
    val currentPatterns: List<PatternDefinitionView>)
enum class PatternApprovalStatus { BUNDLED, PREVIEW_ONLY, APPROVED }
data class PatternApprovalSettings(val operation: PatternOperation?, val accountLast4: String?,
    val referenceField: String?, val contextReferences: Set<String>, val currency: Currency?)
data class PatternDefinitionView(val id: String, val bankId: String, val label: String, val revision: Int,
    val enabled: Boolean, val status: PatternApprovalStatus, val example: String, val fields: List<String>,
    val priorRevisions: List<Int>, val approval: PatternApprovalSettings? = null) {
    val canApprove: Boolean get() = status != PatternApprovalStatus.BUNDLED
    val canApply: Boolean get() = enabled && status != PatternApprovalStatus.PREVIEW_ONLY
    val referenceFields: List<String> get() = fields.filter {
        it in setOf("source_account", "destination_account", "account_reference", "funding_reference", "counterparty_bank")
    }
    fun accountReferenceFields(operation: PatternOperation?): List<String> {
        val ownRole = if (operation in setOf(PatternOperation.TRANSFER_IN, PatternOperation.REFUND)) "destination_account" else "source_account"
        return referenceFields.filter { it == "account_reference" || it == ownRole }
    }
}

internal fun MessagePatternConfiguration.toCatalog() = PatternCatalog(generation, banks.map { bank ->
    PatternBankView(bank.bankId, bank.displayName, bank.senders.toList(), bank.currentPatterns.map { pattern ->
        val status = when {
            pattern.roleProfile != null -> PatternApprovalStatus.BUNDLED
            pattern.factContract == null -> PatternApprovalStatus.PREVIEW_ONLY
            else -> PatternApprovalStatus.APPROVED
        }
        val example = pattern.roleProfile?.rules?.single()?.phrases?.joinToString(" · ")
            ?: pattern.parts.joinToString("") { part -> when (part) {
                is StaticText -> part.value
                is FieldSlot -> "…"
            } }
        PatternDefinitionView(pattern.id, bank.bankId, pattern.label, pattern.revision, pattern.enabled, status,
            example.take(240), pattern.parts.filterIsInstance<FieldSlot>().filterNot { it.ignored }.map { it.field },
            bank.patterns.filter { it.id == pattern.id && it.revision < pattern.revision }.map { it.revision },
            pattern.factContract?.let { contract -> PatternApprovalSettings(
                PatternOperation.entries.firstOrNull { it.name == contract.family.name },
                contract.accountBindings.singleOrNull()?.last4, contract.accountBindings.singleOrNull()?.field,
                contract.contextReferences, contract.currency?.let { Currency.valueOf(it.name) }) })
    })
})
