package com.baraa.masroof.lab.beta.learning

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.data.analytics.*
import com.baraa.masroof.data.learning.LearningDatabase
import com.baraa.masroof.testsupport.TestStorageContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native SQLite migration and restore evidence in isolated device storage. */
@RunWith(AndroidJUnit4::class)
class AdvisoryLifecycleDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = TestStorageContext(instrumentation.targetContext)
    private fun createV1(name: String, type: String, insert: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val source=instrumentation.context.assets.open("$type/1.json").bufferedReader().use { it.readText() }
                    val schema=Json.parseToJsonElement(source).jsonObject.getValue("database").jsonObject
                    schema.getValue("entities").jsonArray.forEach { element ->
                        val entity=element.jsonObject
                        val table=entity.getValue("tableName").jsonPrimitive.content
                        db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                        entity["indices"]?.jsonArray.orEmpty().forEach { index -> db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table)) }
                    }
                    schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                    insert(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
    }
    @Test fun migrateLearningV1PreservesTheAuditWithoutInventingScores() = runBlocking {
        val name="device-learning-upgrade.db"
        createV1(name,LearningDatabase::class.java.name) { it.execSQL("INSERT INTO engine_interpretation VALUES ('audit','raw','BANK_ALJAZIRA','engine','rules','model','ML','FEE','SUCCESS','','hash',1,2)") }
        val database=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(LearningDatabase.MIGRATION_1_2).build()
        try {
            val row=database.learning().history("raw").single()
            assertEquals("ML",row.origin)
            assertNull(row.score)
            assertEquals("LEGACY_UNKNOWN",row.assistOutcome)
            assertTrue(database.learning().feedback().isEmpty())
        } finally { database.close();context.deleteDatabase(name) }
    }
    @Test fun migrateAnalyticsAndRestoreKeepBothPublishedEstimatesImmutable() = runBlocking {
        val name="device-analysis-upgrade.db"
        createV1(name,AnalyticsDatabase::class.java.name) { it.execSQL("INSERT INTO analysis_preferences VALUES (1,9000,0,1,1,1,3,0,1,10,'100')") }
        val database=Room.databaseBuilder(context,AnalyticsDatabase::class.java,name).addMigrations(AnalyticsDatabase.MIGRATION_1_2).build()
        try {
            val dao=database.analytics()
            assertEquals("100",dao.preferences()!!.gapDays)
            val pair=ForecastComparisonRow("pair",100,130,10,"a".repeat(64),"model","evaluation",200,300)
            dao.comparison(pair)
            assertEquals(-1L,dao.comparison(pair.copy(personalTotal=999)))
            dao.evaluateComparison(pair.id,250,"b".repeat(64),20)
            val actual=dao.comparisons().single()
            assertEquals(pair.personalTotal,actual.personalTotal)
            assertEquals(pair.baselineTotal,actual.baselineTotal)
            assertEquals(250L,actual.actual)
            val gap=AnalysisCoverageRow("gap",100,100,"LEGACY",10)
            dao.coverage(listOf(gap))
            val archive=AnalyticsArchiveStore.export(database)
            AnalyticsArchiveStore.restore(database,null)
            AnalyticsArchiveStore.restore(database,archive)
            assertEquals(listOf(gap),dao.coverage())
            assertEquals(listOf(actual),dao.comparisons())
        } finally { database.close();context.deleteDatabase(name) }
    }
}
