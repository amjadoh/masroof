package com.baraa.masroof.lab.beta.data
import androidx.room.*
@Dao
internal interface BetaShellMarkerDao {
    @Query("SELECT COUNT(DISTINCT targetId) FROM beta_audit_log WHERE eventKind='LAB_MARKER'") suspend fun count(): Int
    @Insert suspend fun appendMarker(row: BetaAuditRecord)
    suspend fun upsert(entity: BetaShellMarkerEntity) { appendMarker(BetaAuditRecord(java.util.UUID.randomUUID().toString(),"LAB_MARKER",targetId=entity.id,payload=entity.note)) }
}
