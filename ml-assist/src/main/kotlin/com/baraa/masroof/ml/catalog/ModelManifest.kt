package com.baraa.masroof.ml.catalog

import com.baraa.masroof.ml.family.FamilyInputCleaner
import com.baraa.masroof.ml.family.SmsFamilyLabel

/** Only installed models are registered; a roadmap entry is not an installed capability. */
data class ModelManifest(
    val task: String, val modelVersion: String, val cleaningVersion: String, val featuresVersion: String,
    val enabledFamilies: Set<SmsFamilyLabel>, val evaluationSource: String,
) {
    fun requireCompatible() {
        require(task == "sms-family" && modelVersion.isNotBlank())
        require(cleaningVersion == FamilyInputCleaner.VERSION && featuresVersion == FamilyInputCleaner.FEATURES_VERSION)
        require(evaluationSource == "ML_SEED_EVALUATION.json")
    }
}
