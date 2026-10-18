package com.baraa.masroof.lab.beta.data

import androidx.room.AutoMigration
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
    entities = [BetaShellMarkerEntity::class, BetaAssetEntity::class, BetaAssetAliasEntity::class, BetaBankSelectionEntity::class,
        BetaRawEvidenceEntity::class, BetaEvidenceOutcomeEntity::class, BetaEvidenceRetryEntity::class, BetaEvidenceDecisionEntity::class,
        BetaLedgerCurrentEntity::class, BetaLedgerHistoryEntity::class, BetaLedgerEvidenceClaimEntity::class, BetaLedgerProofClaimEntity::class,
        BetaCorrectionCurrentEntity::class, BetaCorrectionAuditEntity::class, BetaCorrectionEvidenceClaimEntity::class, BetaCorrectionProofClaimEntity::class,
        BetaFinancialAdoptionEntity::class, BetaFinancialApprovalRetryEntity::class, BetaDashboardFactsEntity::class, BetaDashboardAuxFactsEntity::class, BetaDashboardConfigurationEntity::class, BetaLearningLabelEntity::class],
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11)],
    version = BetaDatabase.VERSION,
    exportSchema = true,
)
abstract class BetaDatabase : RoomDatabase() {
    abstract fun bankSelectionDao(): BetaBankSelectionDao
    internal abstract fun assetDao(): BetaAssetDao
    internal abstract fun learningDao(): BetaLearningDao
    internal abstract fun evidenceDao(): BetaEvidenceDao
    internal abstract fun ledgerDao(): BetaLedgerDao
    internal abstract fun correctionDao(): BetaCorrectionDao
    internal abstract fun financialAdoptionDao(): BetaFinancialAdoptionDao
    internal abstract fun dashboardFactsDao(): BetaDashboardFactsDao
    internal abstract fun dashboardAuxFactsDao(): BetaDashboardAuxFactsDao
    internal abstract fun dashboardConfigurationDao(): BetaDashboardConfigurationDao
    abstract fun shellMarkerDao(): BetaShellMarkerDao

    companion object {
        const val NAME: String = BetaEnvironment.DATABASE_NAME
        const val VERSION: Int = 11
    }
}
