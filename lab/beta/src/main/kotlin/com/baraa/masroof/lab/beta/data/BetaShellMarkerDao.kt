package com.baraa.masroof.lab.beta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BetaShellMarkerDao {
    @Query("SELECT COUNT(*) FROM beta_shell_marker")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BetaShellMarkerEntity)
}
