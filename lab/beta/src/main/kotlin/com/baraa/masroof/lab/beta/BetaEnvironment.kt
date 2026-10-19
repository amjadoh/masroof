package com.baraa.masroof.lab.beta

/**
 * Names and guards for the isolated Masroof Beta process state.
 *
 * Beta never opens the production database, the parsing-lab database, or a
 * production preference file. Migration rehearsals use a third file later;
 * this shell does not open that file either.
 */
object BetaEnvironment {
    const val DATABASE_NAME: String = "masroof_beta_v2.db"
    const val MIGRATION_STAGING_DATABASE_NAME: String = "masroof_beta_migration_test.db"
    const val PREFS_NAME: String = "masroof_beta_v2_prefs"
    const val BACKUP_DATABASE_FILE_NAME: String = "masroof_beta_v2_backup.db"
    const val BACKUP_PREFS_FILE_NAME: String = "masroof_beta_v2_prefs_backup.txt"
    const val PRODUCTION_DATABASE_NAME: String = "masroof.db"
    const val PARSING_LAB_DATABASE_NAME: String = "masroof_parsing_lab.db"

    val forbiddenDatabaseNames: Set<String> = setOf(
        "masroof_beta.db",
        PRODUCTION_DATABASE_NAME,
        PARSING_LAB_DATABASE_NAME,
        MIGRATION_STAGING_DATABASE_NAME,
    )

    /**
     * Known production preference file names. Listed as literals so this module
     * does not import production packages.
     */
    val forbiddenPreferenceNames: Set<String> = setOf(
        "masroof_beta_prefs",
        "onboarding_prefs",
        "app_locale_prefs",
        "theme_prefs",
        "dashboard_layout_prefs",
        "chart_data_preferences",
        "notification_prefs",
        "github_token_prefs",
        "pending_update_prefs",
        "update_check_prefs",
        "masroof_maintenance",
    )

    fun requireBetaDatabaseName(name: String) {
        require(name == DATABASE_NAME) { "Beta opens $DATABASE_NAME only, was $name" }
        require(name !in forbiddenDatabaseNames) { "Beta cannot open forbidden database $name" }
    }

    fun requireBetaPreferenceName(name: String) {
        require(name == PREFS_NAME) { "Beta preferences are $PREFS_NAME only, was $name" }
        require(name !in forbiddenPreferenceNames) { "Beta cannot open production preferences $name" }
    }

    fun requireBackupDatabaseName(name: String) {
        require(name == BACKUP_DATABASE_FILE_NAME) { "Beta backup file is fixed, was $name" }
        require(name !in forbiddenDatabaseNames) { "Beta backup cannot replace $name" }
        require(name != DATABASE_NAME) { "Beta backup must not overwrite the live beta database name" }
    }

    fun requireBackupPreferenceName(name: String) {
        require(name == BACKUP_PREFS_FILE_NAME) { "Beta preference backup is fixed, was $name" }
        require(name !in forbiddenPreferenceNames) { "Beta preference backup cannot replace $name" }
    }
}
