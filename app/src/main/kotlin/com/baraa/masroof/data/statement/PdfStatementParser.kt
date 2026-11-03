package com.baraa.masroof.data.statement

import android.content.Context
import com.baraa.masroof.domain.statement.AccountStatementParser
import com.baraa.masroof.domain.statement.StatementMatchPolicy
import com.baraa.masroof.domain.statement.StatementParseResult
import com.baraa.masroof.domain.statement.StatementRejection
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * A bank statement PDF, read in memory. The account is the one owned candidate whose number
 * the statement prints; the movements come from [RunningBalanceStatementReader].
 * Encrypted or image-only PDFs are unreadable. Nothing is written to disk or logged.
 */
class PdfStatementParser(private val context: Context) : AccountStatementParser {
    override fun parse(stream: InputStream, candidates: Set<StatementMatchPolicy.QualifiedAccount>): StatementParseResult {
        val texts = runCatching { extract(stream) }.getOrNull()
            ?: return StatementParseResult.Rejected(StatementRejection.UNREADABLE)
        val account = StatementAccountMatcher.match(texts.first(), candidates)
            ?: return StatementParseResult.Rejected(StatementRejection.UNOWNED_ACCOUNT)
        // Page order first; content order for layouts whose columns share a printed line.
        val results = texts.map { RunningBalanceStatementReader.read(it.lines(), account) }
        return results.firstOrNull { it is StatementParseResult.Accepted } ?: results.first()
    }

    private fun extract(stream: InputStream): List<String> {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(stream).use { document ->
            require(!document.isEncrypted)
            require(document.numberOfPages <= MAX_PAGES)
            listOf(true, false).map { byPosition -> PDFTextStripper().apply { sortByPosition = byPosition }.getText(document) }
        }
    }

    private companion object {
        const val MAX_PAGES = 200
    }
}

/**
 * Picks the candidate account the statement is about: its number ends a long printed number
 * (account number or IBAN). When several candidates do, the one printed most often wins;
 * a tie identifies nothing.
 */
object StatementAccountMatcher {
    fun match(text: String, candidates: Set<StatementMatchPolicy.QualifiedAccount>): StatementMatchPolicy.QualifiedAccount? {
        val numbers = Regex("\\d{10,}").findAll(text).map { it.value }.toList()
        val counts = candidates.associateWith { candidate ->
            val digits = candidate.accountMasked.filter(Char::isDigit)
            if (digits.length < 4) 0 else numbers.count { it.endsWith(digits) }
        }.filterValues { it > 0 }
        val best = counts.maxOfOrNull { it.value } ?: return null
        return counts.filterValues { it == best }.keys.singleOrNull()
    }
}
