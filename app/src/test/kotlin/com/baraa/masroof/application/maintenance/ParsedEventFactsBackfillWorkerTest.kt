package com.baraa.masroof.application.maintenance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParsedEventFactsBackfillWorkerTest {
    @Test fun alreadyQueuedRetiredWorkFinishesWithoutBackfilling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<ParsedEventFactsBackfillWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
