package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService
import com.baraa.masroof.lab.copy.bank.BankRoutingResult
import com.baraa.masroof.lab.copy.bank.BankSmsRegistry
import com.baraa.masroof.lab.copy.bank.aljazira.AlJaziraSmsAdapter
import com.baraa.masroof.lab.finance.value.BankId

/** Uses every preserved lab adapter, including normalized sender and suspicion handling. */
class LabEvidenceRouter : EvidenceRouter {
    private val registry = BankSmsRegistry(listOf(AlJaziraSmsAdapter()))
    override fun route(input: EvidenceMessage): EvidenceRoute = when (val route = registry.route(input.sender, input.body)) {
        is BankRoutingResult.Matched -> EvidenceRoute(RouteKind.MATCHED, setOf(BankId(route.adapter.bank.id)), VERSION)
        is BankRoutingResult.Ambiguous -> EvidenceRoute(RouteKind.AMBIGUOUS,
            route.candidates.map { BankId(it.adapter.bank.id) }.toSet(), VERSION)
        is BankRoutingResult.SuspectedBank -> EvidenceRoute(RouteKind.SUSPECTED, emptySet(), VERSION)
        is BankRoutingResult.NotMatched -> EvidenceRoute(RouteKind.UNRECOGNIZED, emptySet(), VERSION)
    }
    companion object { const val VERSION = "preserved-lab-router-v1" }
}

class LabEvidenceComparer(private val service: BetaInterpretationService = BetaInterpretationService.sharedRules()) : EvidenceComparer {
    override fun compare(input: MessageInput, route: EvidenceRoute): EvidenceComparison {
        require(route.kind == RouteKind.MATCHED)
        // This is the result of the full router, never a selected-bank filtered catalog or override.
        val catalog = BankCatalog(route.candidates.map { BankDefinition(it, setOf(input.sender)) })
        val result = service.compare(input, catalog)
        require(result.baseline.evidenceId == input.evidenceId && result.candidate.evidenceId == input.evidenceId)
        require(result.baseline.version.isSupported() && result.candidate.version.isSupported())
        val reasons = buildSet {
            if (result.baseline.facts != result.candidate.facts) add("FACTS_DIFFER")
            if (result.baseline.status != result.candidate.status) add("STATUS_DIFFERS")
            if (result.baseline.bank != result.candidate.bank) add("BANK_DIFFERS")
            if (result.baseline.bank != route.candidates.single() || result.candidate.bank != route.candidates.single()) add("BANK_ROUTE_MISMATCH")
            if (result.baseline.blockers.isNotEmpty() || result.candidate.blockers.isNotEmpty()) add("BLOCKED_INTERPRETATION")
            if (listOf(result.baseline.status, result.candidate.status).any {
                it !in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL)
            }) add("INTERPRETATION_REQUIRES_REVIEW")
        }
        return EvidenceComparison(if (reasons.isEmpty()) EvidenceDisposition.COMPARISON_ONLY else EvidenceDisposition.REVIEW,
            reasons, InterpretationArchive.encode(result.baseline), InterpretationArchive.encode(result.candidate))
    }
}
