package com.baraa.masroof.lab.beta

/** Legacy host-test sentinels. None of the protected names is opened by the Lab runtime. */
object BetaEnvironment {
    const val DATABASE_NAME="lab_replica_masroof.db"
    const val MIGRATION_STAGING_DATABASE_NAME="masroof_beta_migration_test.db"
    const val PARSING_LAB_DATABASE_NAME="masroof_parsing_lab.db"
    val forbiddenPreferenceNames=setOf("masroof_beta_prefs","onboarding_prefs","app_locale_prefs","theme_prefs",
        "dashboard_layout_prefs","chart_data_preferences","notification_prefs","github_token_prefs",
        "pending_update_prefs","update_check_prefs","masroof_maintenance")
}
