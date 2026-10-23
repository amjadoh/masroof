package com.baraa.masroof.architecture

import com.baraa.masroof.data.room.MasroofDatabase
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Extraction is not a schema migration. Baseline: main after #317. */
class FinancialSchemaCompatibilityTest {
    @Test
    fun financialDatabaseRetainsItsVersionAndExportedSchema() {
        assertEquals("masroof.db", MasroofDatabase.NAME)
        assertEquals(18, MasroofDatabase.VERSION)
        assertEquals("71f1193767b3aa12bcb6cc6e7ce77147", MasroofDatabase.IDENTITY_HASH)
        val schemas = File("schemas/com.baraa.masroof.data.room.MasroofDatabase")
        assertEquals((1..18).toSet(), schemas.listFiles()!!.map { it.nameWithoutExtension.toInt() }.toSet())
        val digest = MessageDigest.getInstance("SHA-256").digest(File(schemas, "18.json").readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals("10d89f9fa6411036dd4470592a25de89c987e332512a4918bceab3de40d849a7", digest)
    }
}
