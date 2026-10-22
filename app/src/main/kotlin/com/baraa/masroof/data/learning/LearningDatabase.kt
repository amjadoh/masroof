package com.baraa.masroof.data.learning

import androidx.room.*

/** Advisory provenance. Financial authority remains in the unchanged fourteen-table DB. */
@Entity(tableName="engine_interpretation",indices=[Index("rawId")])
data class EngineInterpretationRow(
    @PrimaryKey val id: String,val rawId: String,val bankId: String?,val engineVersion: String,val rulesVersion: String,
    val activeModelVersion: String?,val origin: String,val family: String,val status: String,val blockers: String,
    val bodyHash: String,val receivedAt: Long,val interpretedAt: Long,
)
@Entity(tableName="learning_label",indices=[Index("rawId"),Index("bankId","template")])
data class LearningLabelRow(
    @PrimaryKey val decisionId: String,val rawId: String,val bankId: String,val family: String,val template: String,
    val featuresJson: String,val featuresVersion: String,val sourceAt: Long,val provenance: String,val bodyHash: String,
)
@Dao
interface LearningDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun record(row: EngineInterpretationRow)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun labels(rows: List<LearningLabelRow>)
    @Query("DELETE FROM learning_label") suspend fun clearLabels()
    @Transaction suspend fun replaceLabels(rows: List<LearningLabelRow>) {clearLabels();if(rows.isNotEmpty()) labels(rows)}
    @Query("SELECT * FROM learning_label ORDER BY sourceAt,decisionId") suspend fun labels(): List<LearningLabelRow>
    @Query("SELECT * FROM engine_interpretation WHERE rawId=:rawId ORDER BY interpretedAt") suspend fun history(rawId: String): List<EngineInterpretationRow>
}
@Database(entities=[EngineInterpretationRow::class,LearningLabelRow::class],version=1,exportSchema=true)
abstract class LearningDatabase : RoomDatabase() { abstract fun learning(): LearningDao }
