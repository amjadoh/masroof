package com.baraa.masroof.lab.beta

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BetaEnvironmentTest {
    @Test
    fun guardsRejectForbiddenNames() {
        assertFails { BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.PRODUCTION_DATABASE_NAME) }
        assertFails { BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.PARSING_LAB_DATABASE_NAME) }
        assertFails {
            BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.MIGRATION_STAGING_DATABASE_NAME)
        }
        assertFails { BetaEnvironment.requireBackupDatabaseName(BetaEnvironment.PRODUCTION_DATABASE_NAME) }
        BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
            assertFails { BetaEnvironment.requireBetaPreferenceName(name) }
        }
        BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.DATABASE_NAME)
        BetaEnvironment.requireBetaPreferenceName(BetaEnvironment.PREFS_NAME)
        assertEquals("masroof_beta_v2.db", BetaEnvironment.DATABASE_NAME)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        fail("Expected the beta guard to reject this name")
    }
}
