package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.SenderRouter
import com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService
import com.baraa.masroof.lab.finance.value.BankId

/** Identity configuration only, independent of any parser or user-enabled import scope. */
internal object BetaEngineRouting {
    val router = SenderRouter(listOf(BankDefinition(BankId("BANK_ALJAZIRA"), setOf(
        "AlJazira", "JaziraBank", "BankAlJazira", "AlJaziraBank",
        "AlJazira-AD", "JaziraBank-AD", "BankAlJazira-AD", "AlJaziraBank-AD",
        "بنك الجزيرة", "بنك الجزيره",
    ))), listOf("بنك الجزيرة", "بنك الجزيره", "bank aljazira", "aljazira bank"))
}

/** Full independent sender catalog. Plausible sender/body text is review-only. */
class LabEvidenceRouter : EvidenceRouter {
    override fun route(input: EvidenceMessage): EvidenceRoute {
        val candidates = BetaEngineRouting.router.catalog(input.sender).candidates(input.sender)
        val kind = when {
            candidates.size > 1 -> RouteKind.AMBIGUOUS
            candidates.size == 1 -> RouteKind.MATCHED
            BetaEngineRouting.router.suspected(input.sender, input.body) -> RouteKind.SUSPECTED
            else -> RouteKind.UNRECOGNIZED
        }
        return EvidenceRoute(kind, candidates, VERSION)
    }
    companion object { const val VERSION = "bank-engine-sender-router-v1" }
}

/** Existing injection boundary; new outcomes contain one current interpretation only. */
class LabEvidenceComparer(private val service: BetaInterpretationService = BetaInterpretationService.independent()) : EvidenceComparer {
    override fun compare(input: MessageInput, route: EvidenceRoute): EvidenceComparison {
        require(route.kind == RouteKind.MATCHED)
        if (service.isHistoricalComparison) return historical(input, route)
        val catalog = BetaEngineRouting.router.catalog(input.sender)
        require(catalog.candidates(input.sender) == route.candidates) { "Route changed" }
        val result = service.interpret(input, catalog)
        require(result.evidenceId == input.evidenceId && result.version.isSupported())
        val reasons = buildSet {
            if (result.bank != route.candidates.single()) add("BANK_ROUTE_MISMATCH")
            if (result.blockers.isNotEmpty()) add("BLOCKED_INTERPRETATION")
            if (result.status !in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL)) add("INTERPRETATION_REQUIRES_REVIEW")
        }
        // Retain old Room columns without a destructive rewrite. Baseline is inert, never a duplicate result.
        return EvidenceComparison(if (reasons.isEmpty()) EvidenceDisposition.INTERPRETED else EvidenceDisposition.REVIEW,
            reasons, "{}", InterpretationArchive.encode(result))
    }

    /** Explicit historical regression inputs only; never the Beta default. */
    private fun historical(input: MessageInput, route: EvidenceRoute): EvidenceComparison {
        val result = service.compare(input, BankCatalog(route.candidates.map { BankDefinition(it, setOf(input.sender)) }))
        require(result.baseline.evidenceId == input.evidenceId && result.candidate.evidenceId == input.evidenceId)
        require(result.baseline.version.isSupported() && result.candidate.version.isSupported())
        val reasons = buildSet {
            if (result.baseline.facts != result.candidate.facts) add("FACTS_DIFFER")
            if (result.baseline.status != result.candidate.status) add("STATUS_DIFFERS")
            if (result.baseline.bank != result.candidate.bank) add("BANK_DIFFERS")
            if (result.baseline.bank != route.candidates.single() || result.candidate.bank != route.candidates.single()) add("BANK_ROUTE_MISMATCH")
            if (result.baseline.blockers.isNotEmpty() || result.candidate.blockers.isNotEmpty()) add("BLOCKED_INTERPRETATION")
            if (listOf(result.baseline.status, result.candidate.status).any { it !in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL) }) add("INTERPRETATION_REQUIRES_REVIEW")
        }
        return EvidenceComparison(if (reasons.isEmpty()) EvidenceDisposition.COMPARISON_ONLY else EvidenceDisposition.REVIEW,
            reasons, InterpretationArchive.encode(result.baseline), InterpretationArchive.encode(result.candidate))
    }
}
