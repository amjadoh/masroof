package com.baraa.masroof.lab.beta.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baraa.masroof.lab.beta.BetaEnvironment

/**
 * Independent Beta Room schema.
 *
 * Starts at version 1 inside the lab module. It is not the production
 * [masroof.db] schema and must not be replaced by that type permanently.
 */
@Database(
    entities = [BetaShellMarkerEntity::class],
    version = BetaDatabase.VERSION,
    exportSchema = true,
)
abstract class BetaDatabase : RoomDatabase() {
    abstract fun shellMarkerDao(): BetaShellMarkerDao

    companion object {
        const val NAME: String = BetaEnvironment.DATABASE_NAME
        const val VERSION: Int = 1
    }
}
