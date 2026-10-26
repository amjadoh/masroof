package com.baraa.masroof.integration.learning

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.analytics.*
import com.baraa.masroof.data.learning.LearningDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class AdvisoryLifecycleMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun createV1(name: String, type: String, version: Int = 1, insert: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val schema=Json.parseToJsonElement(java.io.File("schemas/$type/$version.json").readText()).jsonObject.getValue("database").jsonObject
                    for(element in schema.getValue("entities").jsonArray) {
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
    @Test fun learningMigrationPreservesOldProvenanceWithoutInventingRuntimeParticipation() = runBlocking {
        val name="learning-migrate.db"
        createV1(name,LearningDatabase::class.java.name) { db ->
            db.execSQL("INSERT INTO engine_interpretation VALUES ('audit','raw','BANK_ALJAZIRA','engine','rules','model','ML','FEE','SUCCESS','','hash',1,2)")
        }
        val db=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(LearningDatabase.MIGRATION_1_2).build()
        try {
            val row=db.learning().history("raw").single()
            assertEquals("ML",row.origin)
            assertFalse(row.assistRequested)
            assertEquals("LEGACY_UNKNOWN",row.assistOutcome)
            assertNull(row.score)
            assertTrue(db.learning().feedback().isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun analyticsMigrationAndNewArchiveKeepCoverageAndPublishedPairsAcrossRestore() = runBlocking {
        val name="analysis-migrate.db"
        createV1(name,AnalyticsDatabase::class.java.name) { db ->
            db.execSQL("INSERT INTO analysis_preferences VALUES (1,9000,0,1,1,1,3,0,1,10,'100')")
        }
        val db=Room.databaseBuilder(context,AnalyticsDatabase::class.java,name).addMigrations(AnalyticsDatabase.MIGRATION_1_2,AnalyticsDatabase.MIGRATION_2_3,AnalyticsDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("100",db.analytics().preferences()!!.gapDays)
            val gap=AnalysisCoverageRow("gap",100,100,"LEGACY",10)
            val pair=ForecastComparisonRow("pair",100,130,10,"a".repeat(64),"model","evaluation",200,300)
            db.analytics().coverage(listOf(gap));db.analytics().comparison(pair)
            val archive=AnalyticsArchiveStore.export(db)
            AnalyticsArchiveStore.restore(db,null)
            assertTrue(db.analytics().coverage().isEmpty())
            AnalyticsArchiveStore.restore(db,archive)
            assertEquals(listOf(gap),db.analytics().coverage())
            assertEquals(listOf(pair),db.analytics().comparisons())
            assertNotNull(AnalyticsArchiveStore.validate("{\"version\":1}"))
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun analyticsV2UpgradeAndOldArchivePreserveTheOriginalComparisonOrigin() = runBlocking {
        val name="analysis-v2-upgrade.db"
        createV1(name,AnalyticsDatabase::class.java.name,version=2) { db ->
            db.execSQL("INSERT INTO analysis_forecast_comparison VALUES ('legacy',100,130,10,'${"a".repeat(64)}','model','published-day8-v1',200,300,250,'${"b".repeat(64)}',20)")
        }
        val database=Room.databaseBuilder(context,AnalyticsDatabase::class.java,name)
            .addMigrations(AnalyticsDatabase.MIGRATION_1_2,AnalyticsDatabase.MIGRATION_2_3,AnalyticsDatabase.MIGRATION_3_4).build()
        try {
            val pair=database.analytics().comparisons().single()
            assertEquals(107L,pair.originDay)
            assertEquals(200L,pair.personalTotal)
            assertEquals(300L,pair.baselineTotal)
            assertEquals(250L,pair.actual)
            val exported=AnalyticsArchiveStore.export(database)
            assertEquals(4,AnalyticsArchiveStore.validate(exported)!!.version)
            val old=Json.parseToJsonElement(exported).jsonObject.toMutableMap().apply {
                put("version",JsonPrimitive(2))
                put("comparisons",JsonArray(listOf(JsonObject(Json.parseToJsonElement(exported).jsonObject
                    .getValue("comparisons").jsonArray.single().jsonObject.filterKeys { it != "originDay" }))))
            }
            AnalyticsArchiveStore.restore(database,JsonObject(old).toString())
            assertEquals(listOf(pair),database.analytics().comparisons())
        } finally { database.close();context.deleteDatabase(name) }
    }

}
