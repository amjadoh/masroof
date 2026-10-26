package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.data.room.DatabaseRestartRequiredException
import com.baraa.masroof.lab.bank.patterns.BankMessagePatterns
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MessagePatternsWorkflowTest {
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
