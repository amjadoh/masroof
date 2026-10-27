package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.data.room.DatabaseRestartRequiredException
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MessagePatternsWorkflowTest {
    @Test fun bankEditsKeepIdentityAndRejectCrossBankSendersWithoutChangingTheCatalog() = runBlocking {
        val directory = Files.createTempDirectory("masroof-banks-").toFile()
        try {
            val workflow = MessagePatternsWorkflow(MessagePatternStore(directory.resolve("patterns.json")), DatabaseAccessGate())
            workflow.addBank(0, "First bank", "FIRST", "FIRST_BANK")
            workflow.addBank(1, "Second bank", "SECOND", "SECOND_BANK")
            workflow.updateBank(2, "FIRST_BANK", "Renamed bank", listOf("FIRST", "FIRST_ALIAS"))
            val saved = workflow.catalog()
            assertEquals("Renamed bank", saved.banks.single { it.bankId == "FIRST_BANK" }.displayName)
            assertEquals(listOf("FIRST", "FIRST_ALIAS"), saved.banks.single { it.bankId == "FIRST_BANK" }.senders)
            assertTrue(runCatching { workflow.updateBank(3, "FIRST_BANK", "Conflict", listOf("SECOND")) }.isFailure)
            assertTrue(runCatching { workflow.addBank(3, "Duplicate", "FIRST_ALIAS") }.isFailure)
            assertEquals(saved, workflow.catalog())
        } finally { directory.deleteRecursively() }
    }

    @Test fun teachingRequiresPreviewAndPreservesRevisionsWithoutFinancialWrites() = runBlocking {
        val directory = Files.createTempDirectory("masroof-teaching-").toFile()
        try {
            val workflow = MessagePatternsWorkflow(MessagePatternStore(directory.resolve("patterns.json")), DatabaseAccessGate())
            workflow.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank")))
            val body = "Payment\nAmount:10SAR\nTo:SHOP"
            val bindings = listOf(PatternBinding("amount", body.indexOf("10SAR"), body.indexOf("10SAR") + 5),
                PatternBinding("merchant", body.indexOf("SHOP"), body.length))
            val preview = workflow.teach(1, "STC", "STC Bank", PatternOperation.PURCHASE, body, bindings)
            assertEquals("10SAR", preview.fields.getValue("amount").text)
            assertTrue(workflow.load().banks.single().patterns.isEmpty())
            assertEquals("250SAR", workflow.tryPreview(preview, "STC Bank", body.replace("10SAR", "250SAR"))!!.getValue("amount").text)
            assertNull(workflow.tryPreview(preview, "OTHER", body))
            val saved = workflow.saveTeaching(preview)
            val id = saved.banks.single().currentPatterns.single().id
            val stored = saved.banks.single().currentPatterns.single()
            assertNotNull(stored.preview("STC", "STC Bank", body))
            assertNull(stored.preview("OTHER", "STC Bank", body))
            workflow.setEnabled(2, "STC", id, false)
            assertNull(workflow.load().banks.single().currentPatterns.single().preview("STC", "STC Bank", body))
            assertTrue(runCatching { workflow.saveTeaching(preview) }.isFailure)
            workflow.restoreRevision(3, "STC", id, 1)
            val restored = workflow.load().banks.single()
            val versions = restored.patterns
            assertEquals(listOf(1, 2, 3), versions.map { it.revision })
            assertNotNull(restored.currentPatterns.single().preview("STC", "STC Bank", body))
            assertTrue(runCatching { workflow.teach(4, "STC", "STC Bank", PatternOperation.PURCHASE, body,
                listOf(PatternBinding("merchant", body.indexOf("SHOP"), body.length))) }.isFailure)
            assertTrue(runCatching { workflow.teach(4, "STC", "STC Bank", PatternOperation.PURCHASE, body,
                bindings + PatternBinding("source_account", 0, 7)) }.isFailure)
        } finally { directory.deleteRecursively() }
    }

    @Test fun editsWaitForAdmittedFinancialWorkAndRetiredInstancesCannotEdit() = runBlocking {
        val directory = Files.createTempDirectory("masroof-pattern-gate-").toFile()
        try {
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            val gate = DatabaseAccessGate()
            val workflow = MessagePatternsWorkflow(store, gate, Dispatchers.Unconfined)
            val release = CompletableDeferred<Unit>()
            val reader = async(start = CoroutineStart.UNDISPATCHED) { gate.withAccess { release.await() } }
            val saving = async(start = CoroutineStart.UNDISPATCHED) {
                workflow.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank")))
            }
            assertFalse(saving.isCompleted)
            assertEquals(0L, store.load().generation)
            release.complete(Unit)
            reader.await()
            assertEquals(1L, saving.await().generation)
            gate.retire()
            assertTrue(runCatching { workflow.saveBank(1, BankMessagePatterns("OTHER", listOf("Other"))) }
                .exceptionOrNull() is DatabaseRestartRequiredException)
            assertEquals(listOf("STC"), store.load().banks.map { it.bankId })
        } finally { directory.deleteRecursively() }
    }
}
