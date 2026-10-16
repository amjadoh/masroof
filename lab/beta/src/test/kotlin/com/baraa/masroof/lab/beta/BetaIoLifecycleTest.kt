package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaIoLifecycleTest {
    @Test fun parkedOperationCloseRecreationAndResetAreOrderedWithoutBlockingCaller() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val old = BetaContainer(context)
        val parked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        BetaIo.execute {
            parked.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            old.putMarker("old", "fixture")
            events += "operation"
        }
        assertTrue(parked.await(5, TimeUnit.SECONDS))
        // This enqueue must return while old work is parked; it never waits on its lock.
        BetaIo.execute { old.close(); events += "close" }
        BetaIo.execute {
            BetaContainer(context).use { recreated ->
                assertEquals(1, recreated.markerCount())
                recreated.reset()
                assertEquals(0, recreated.markerCount())
                events += "reset"
            }
            complete.countDown()
        }
        assertFalse(complete.await(100, TimeUnit.MILLISECONDS))
        release.countDown()
        assertTrue(complete.await(10, TimeUnit.SECONDS))
        assertEquals(listOf("operation", "close", "reset"), events)
        BetaContainer(context).use { assertEquals(0, it.markerCount()) }
        assertThrows(IllegalStateException::class.java) { old.putMarker("late", "must not write") }
        BetaContainer.resetOffline(context)
    }
}
