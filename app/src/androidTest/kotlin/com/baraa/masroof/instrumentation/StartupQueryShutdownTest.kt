package com.baraa.masroof.instrumentation

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.data.room.PreservingOpenHelperFactory
import com.baraa.masroof.testsupport.TestStorageContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Hold the first connection before invalidation initialization requests Room's close lock. */
class StartupQueryShutdownTest {
    @Test(timeout = 30_000)
    fun shutdownWaitsForFirstConnectionWithoutCancellingItsRoomQuery() {
        val host = InstrumentationRegistry.getInstrumentation().targetContext
        val context = TestStorageContext(host, "startup-query-shutdown")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val once = AtomicBoolean()
        val factory = SupportSQLiteOpenHelper.Factory { config ->
            val original = config.callback
            val callback = object : SupportSQLiteOpenHelper.Callback(original.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = original.onUpgrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) {
                    original.onOpen(object : SupportSQLiteDatabase by db {
                        override fun execSQL(sql: String) {
                            if (sql.contains("CREATE TEMP TABLE", true) && once.compareAndSet(false, true)) {
                                entered.countDown()
                                check(release.await(20, TimeUnit.SECONDS)) { "First connection barrier was not released" }
                            }
                            db.execSQL(sql)
                        }
                    })
                }
            }
            PreservingOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(config.context)
                .name(config.name).callback(callback).build())
        }
        val c = AppContainer(context, databaseOpenHelperFactory = factory)
        val closed = CountDownLatch(1)
        val closing = Thread { try { c.close() } finally { closed.countDown() } }.apply { isDaemon = true }
        try {
            c.runStartupMaintenance()
            assertTrue("First connection reached the barrier", entered.await(10, TimeUnit.SECONDS))
            closing.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (closing.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED) && System.nanoTime() < deadline) {
                Thread.yield()
            }
            if (c.startupCancelled) {
                // On the unsafe implementation, let close take Room's write lock while
                // SQLiteOpenHelper still owns the connection callback monitor.
                val closingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while ((c.shutdownStage != "DATABASE_CLOSING" || closing.state != Thread.State.BLOCKED) && System.nanoTime() < closingDeadline) Thread.yield()
            }
            assertEquals("Startup must drain before any database close", "STARTUP_DRAINING", c.shutdownStage)
            assertFalse("Cancelling a Room continuation does not stop the connection it is opening", c.startupCancelled)
            assertEquals(1L, closed.count)
        } finally {
            release.countDown()
            if (closing.state == Thread.State.NEW) closing.start()
            val finished = closed.await(5, TimeUnit.SECONDS)
            if (!finished) Thread.getAllStackTraces().forEach { (thread, stack) ->
                android.util.Log.e("StartupShutdown", "${thread.name} ${thread.state}\n${stack.joinToString("\n")}")
            }
            if (finished) File(host.filesDir, "startup-query-shutdown").deleteRecursively()
        }
        assertEquals("CLOSED", c.shutdownStage)
        assertEquals(0L, closed.count)
    }
}
