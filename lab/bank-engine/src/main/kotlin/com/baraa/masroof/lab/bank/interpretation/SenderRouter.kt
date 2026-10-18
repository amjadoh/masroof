package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.BankCatalog
import com.baraa.masroof.lab.bank.BankDefinition

/** Literal full-catalog routing. Suspicion never supplies a bank identity. */
class SenderRouter(definitions: List<BankDefinition>, suspectedTerms: List<String> = emptyList()) {
    private val definitions = definitions.map { it.copy(exactSenders = it.exactSenders.toSet()) }
    private val suspectedTerms = suspectedTerms.map { SourceText(it).text }
    private fun key(value: String) = SourceText(value).text.filter(Char::isLetterOrDigit)
    init {
        require(this.definitions.map { it.id }.distinct().size == this.definitions.size)
        require(this.definitions.all { bank -> bank.exactSenders.all { key(it).isNotEmpty() } })
    }

    /** Materializes every matching definition for the unchanged original sender, including ties. */
    fun catalog(sender: String): BankCatalog = BankCatalog(definitions.filter { bank ->
        bank.exactSenders.any { key(it) == key(sender) }
    }.map { it.copy(exactSenders = setOf(sender)) })

    fun suspected(sender: String, body: String): Boolean {
        val text = SourceText(body).text
        val senderKey = key(sender)
        return definitions.any { bank -> bank.exactSenders.any { key(it).let { stem -> stem.isNotEmpty() && stem in senderKey } } } ||
            suspectedTerms.any { it in text }
    }
}
