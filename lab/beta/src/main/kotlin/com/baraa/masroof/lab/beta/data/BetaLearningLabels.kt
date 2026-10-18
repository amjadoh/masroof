package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** Append-only explicit human annotation. No model/rule prediction is inserted here. */
@Entity(tableName = "beta_learning_label", primaryKeys = ["rawId", "revision"],
    indices = [Index(value = ["decisionId"], unique = true)],
    foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class, parentColumns = ["id"],
        childColumns = ["rawId"], onDelete = ForeignKey.RESTRICT)])
internal data class BetaLearningLabelEntity(val rawId: String, val revision: Long, val decisionId: String,
    val archiveHash: String, val interpretationAttempt: Int, val engineVersion: String, val rulesVersion: String,
    val field: String, val value: String, val start: Int, val end: Int, val originalText: String,
    val provenance: String, val createdAt: String,
    @ColumnInfo(defaultValue = "NULL") val tokenStart: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val tokenEnd: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val tokenizerVersion: String? = null,
    @ColumnInfo(defaultValue = "NULL") val modelVersion: String? = null,
    @ColumnInfo(defaultValue = "NULL") val humanProvenance: String? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceReceivedAt: String? = null) {
    override fun toString() = "BetaLearningLabelEntity(redacted)"
}

@Dao
internal interface BetaLearningDao {
    @Query("SELECT * FROM beta_learning_label WHERE rawId=:id ORDER BY revision")
    suspend fun history(id: String): List<BetaLearningLabelEntity>
    @Query("SELECT * FROM beta_learning_label WHERE decisionId=:id")
    suspend fun decision(id: String): BetaLearningLabelEntity?
    @Insert suspend fun append(label: BetaLearningLabelEntity)
}
