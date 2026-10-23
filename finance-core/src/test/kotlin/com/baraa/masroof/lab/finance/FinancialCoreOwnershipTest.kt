package com.baraa.masroof.lab.finance

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent empty source scans or an app-side copy from undoing the extraction. */
class FinancialCoreOwnershipTest {
    private val sourcePath = "src/main/kotlin/com/baraa/masroof"
    private val core = File(sourcePath)
    private val app = File("../app/$sourcePath")

    @Test
    fun extractedPoliciesRemainOwnedByFinanceCore() {
        val expected = mapOf(
            "core/money" to listOf("Money", "Currency"),
            "domain/period" to listOf("FinancialPeriodPolicy", "FinancialPeriod", "SalaryCycleStartAdjustment", "CreditCardStatementPolicy"),
            "domain/assembly" to listOf("BankTransactionTimePolicy", "TransactionTiming", "TransactionAssembler"),
            "domain/ids" to listOf("FinancialContainerIdFactory", "FinancialContainerIdParser"),
            "domain/matching" to listOf("TransactionMatcher"),
            "domain/rules" to listOf(
                "TransactionClassifier", "FinancialImpactCalculator", "FinancialImpact", "TransferOwnershipResolver",
                "InformationalMessagePolicy", "ClassificationEvidence", "ClassificationResult", "ResolvedContainerFacts",
            ),
            "domain/statement" to listOf("StatementMatchPolicy", "StatementDirection"),
            "domain/model" to listOf("FinancialTransaction", "ParsedEvent", "Bank"),
        )
        expected.forEach { (directory, names) ->
            names.forEach { name ->
                assertTrue("Missing extracted policy: $directory/$name", File(core, "$directory/$name.kt").isFile)
            }
        }
        val sources = core.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Missing finance core sources", sources.isNotEmpty())
        sources.forEach { file ->
            val relative = file.relativeTo(core)
            assertFalse("Duplicate financial source in app: $relative", File(app, relative.path).exists())
            assertFalse("Financial policies must not perform storage/network I/O: $relative",
                Regex("\\bjava\\.(io|nio\\.file|sql|net)\\.").containsMatchIn(file.readText()))
        }
    }

    @Test
    fun orchestrationAndStatementIoRemainInApp() {
        listOf(
            "domain/ownership/OwnershipResolver.kt",
            "domain/ownership/OwnershipDiscoveryService.kt",
            "domain/ownership/OwnershipConfirmationService.kt",
            "domain/statement/BankStatementEntry.kt",
            "application/transaction/TransactionReconciliationService.kt",
            "application/transaction/ReconciliationCompletionPolicy.kt",
            "parsing/finalize/ParseFinalizer.kt",
        ).forEach { path ->
            assertTrue("Missing app responsibility: $path", File(app, path).isFile)
            assertFalse("App responsibility leaked into finance core: $path", File(core, path).exists())
        }
    }
}
