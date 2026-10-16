@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.baraa.masroof.lab.engine.acceptance

import com.baraa.masroof.lab.copy.parsing.normalizer.MessageNormalizer
import com.baraa.masroof.lab.engine.*
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Offline measurement, never a runtime promotion or posting authority. */
@Serializable
data class ReviewedCase(
    val id: String, val sender: String, val body: String, val receivedAt: String,
    val banks: List<BankProfile>, val expectedStatus: String, val expectedFacts: JsonObject,
    val reviewer: String, val reviewReference: String,
)

@Serializable
data class HoldoutManifest(
    val schema: Int, val provenance: String, val split: String, val frozenAt: String,
    val reviewReference: String, val engineSourceSha256: String, val librarySha256: String,
    val cases: List<ReviewedCase>,
)

@Serializable
data class AcceptancePolicy(
    val reviewReference: String, val minimumCases: Int, val minimumFinancialCases: Int,
    val minimumAcceptedPrecision: Double, val minimumFinancialCoverage: Double,
)

data class CaseMeasurement(val caseHash: String, val expectedStatus: String, val actualStatus: String,
                           val expectedFamily: String?, val differences: Set<String>)

data class AcceptanceAssessment(val blockers: Set<String>, val cases: Int, val accepted: Int,
    val criticalAcceptedErrors: Int, val acceptedPrecision: Double?, val financialCoverage: Double?,
    val measurements: List<CaseMeasurement>) {
    // Measurement prerequisites only; even true is not permission to promote.
    val measurementGatesMet get() = blockers.isEmpty()
}

object M6Acceptance {
    val factNames: Set<String> = LabFacts.serializer().descriptor.let { d ->
        (0 until d.elementsCount).map(d::getElementName).toSet()
    }
    private val statuses = setOf("SUCCESS", "NON_FINANCIAL", "REVIEW_REQUIRED", "PARTIAL", "INVALID", "UNSUPPORTED")
    fun sha(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Body-only: changing sender/ID cannot disguise reuse of a development message. */
    fun messageHash(body: String): String = sha(MessageNormalizer().normalize(body.replace('٬', ',').replace('٫', '.'))
        .comparisonBody.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ").trim())

    fun validate(manifest: HoldoutManifest) {
        require(manifest.schema == 1)
        require(manifest.cases.size in 1..20_000)
        java.time.Instant.parse(manifest.frozenAt)
        require(manifest.reviewReference.isNotBlank())
        require(listOf(manifest.engineSourceSha256, manifest.librarySha256).all { it.matches(Regex("[a-f0-9]{64}")) })
        require(manifest.cases.map { it.id }.toSet().size == manifest.cases.size)
        manifest.cases.forEach { case ->
            require(case.id.isNotBlank() && case.reviewer.isNotBlank() && case.reviewReference.isNotBlank())
            require(case.sender.isNotBlank() && case.sender.length <= 64 && case.body.isNotBlank() && case.body.length <= 16_000)
            require(case.expectedStatus in statuses)
            java.time.Instant.parse(case.receivedAt)
            // All nullable fields must be deliberately annotated. Missing is not a wildcard.
            require(case.expectedFacts.keys == factNames)
            val facts = LabJson.format.decodeFromJsonElement<LabFacts>(case.expectedFacts)
            require(facts.family == null || facts.family in LabFamilies.editable + "UNKNOWN")
            if (case.expectedStatus == "SUCCESS") {
                require(facts.family in LabFamilies.financial && facts.direction in setOf("INCOMING", "OUTGOING"))
                require(facts.amount?.toBigDecimal()?.signum() == 1 && !facts.currency.isNullOrBlank())
            }
            require(case.banks.size in 1..100)
        }
    }

    fun measure(case: ReviewedCase, actual: LabOutcome): CaseMeasurement {
        val observed = LabJson.format.encodeToJsonElement(actual.facts).jsonObject
        val differences = factNames.filter { case.expectedFacts[it] != observed[it] }.toMutableSet()
        if (case.expectedStatus != actual.status) differences += "status"
        return CaseMeasurement(messageHash(case.body), case.expectedStatus, actual.status,
            case.expectedFacts["family"]?.jsonPrimitive?.contentOrNull, differences)
    }

    fun assess(manifest: HoldoutManifest, policy: AcceptancePolicy?, measurements: List<CaseMeasurement>,
               regressionHashes: Set<String>, engineSourceSha256: String, librarySha256: String): AcceptanceAssessment {
        validate(manifest)
        require(measurements.size == manifest.cases.size)
        require(measurements.map { it.caseHash } == manifest.cases.map { messageHash(it.body) })
        val blockers = linkedSetOf<String>()
        if (manifest.provenance != "REVIEWED_REAL_SMS" || manifest.split != "INDEPENDENT_HOLDOUT") blockers += "NOT_DECLARED_REVIEWED_REAL_HOLDOUT"
        if (manifest.engineSourceSha256 != engineSourceSha256 || manifest.librarySha256 != librarySha256) blockers += "CANDIDATE_FINGERPRINT_MISMATCH"
        val hashes = measurements.map { it.caseHash }
        if (hashes.toSet().size != hashes.size) blockers += "DUPLICATE_MESSAGE_CONTENT"
        if (hashes.any { it in regressionHashes }) blockers += "DEVELOPMENT_CORPUS_OVERLAP"
        val accepted = measurements.filter { it.actualStatus == "SUCCESS" }
        val errors = accepted.count { it.differences.isNotEmpty() }
        val financial = measurements.filter { it.expectedStatus == "SUCCESS" }
        val precision = accepted.takeIf { it.isNotEmpty() }?.let { (it.size - errors).toDouble() / it.size }
        val coverage = financial.takeIf { it.isNotEmpty() }?.let { rows ->
            rows.count { it.actualStatus == "SUCCESS" && it.differences.isEmpty() }.toDouble() / rows.size
        }
        if (errors > 0) blockers += "CRITICAL_ACCEPTED_ERRORS"
        if (precision == null) blockers += "NO_ACCEPTED_RESULTS"
        if (coverage == null) blockers += "NO_REVIEWED_FINANCIAL_CASES"
        val reviewAbstentions = setOf("REVIEW_REQUIRED", "PARTIAL", "INVALID", "UNSUPPORTED")
        // Abstaining on reviewed financial messages reduces coverage. It must not be counted
        // as an accepted error or make a reviewed coverage threshold meaningless.
        if (measurements.any { it.differences.isNotEmpty() &&
                !(it.expectedStatus == "SUCCESS" && it.actualStatus in reviewAbstentions) }) {
            blockers += "UNREVIEWED_STATUS_OR_FACT_DIFFERENCES"
        }
        if (policy == null) blockers += "MISSING_REVIEWED_MEASUREMENT_POLICY" else {
            require(policy.reviewReference.isNotBlank() && policy.minimumCases > 0 && policy.minimumFinancialCases > 0)
            require(policy.minimumAcceptedPrecision.isFinite() && policy.minimumAcceptedPrecision in 0.0..1.0)
            require(policy.minimumFinancialCoverage.isFinite() && policy.minimumFinancialCoverage in 0.0..1.0)
            if (measurements.size < policy.minimumCases || financial.size < policy.minimumFinancialCases) blockers += "INSUFFICIENT_REVIEWED_CASES"
            if (precision == null || precision < policy.minimumAcceptedPrecision) blockers += "ACCEPTED_PRECISION_BELOW_POLICY"
            if (coverage == null || coverage < policy.minimumFinancialCoverage) blockers += "FINANCIAL_COVERAGE_BELOW_POLICY"
        }
        return AcceptanceAssessment(blockers, measurements.size, accepted.size, errors, precision, coverage, measurements)
    }

    fun safeReport(result: AcceptanceAssessment): String = buildString {
        appendLine("M6 offline measurement only; no production promotion or financial authority")
        appendLine("Provenance/review declarations require independent human verification; hashes do not establish authenticity")
        appendLine("Measurement gates met: ${result.measurementGatesMet}")
        appendLine("Blockers: ${result.blockers.sorted().joinToString(",")}")
        appendLine("Cases: ${result.cases}; candidate accepted: ${result.accepted}; critical accepted errors: ${result.criticalAcceptedErrors}")
        appendLine("Accepted precision: ${result.acceptedPrecision ?: "undefined"}; exact financial coverage: ${result.financialCoverage ?: "undefined"}")
        appendLine("Full-field exact comparison including nulls and decimal representation; no allowed-difference suppressions")
        appendLine("Device/M0 budget acceptance and explicit promotion approval remain separate required gates")
        result.measurements.groupBy { it.expectedFamily ?: "UNANNOTATED" }.toSortedMap().forEach { (family, rows) ->
            appendLine("Family $family: ${rows.size}; exact=${rows.count { it.differences.isEmpty() }}; accepted_errors=${rows.count { it.actualStatus == "SUCCESS" && it.differences.isNotEmpty() }}")
        }
        // Only generated hashes, closed status names and field names may reach artifacts.
        result.measurements.forEach { row ->
            appendLine("${row.caseHash}: ${row.expectedStatus}->${row.actualStatus}; fields=${row.differences.sorted().joinToString(",")}")
        }
    }
}
