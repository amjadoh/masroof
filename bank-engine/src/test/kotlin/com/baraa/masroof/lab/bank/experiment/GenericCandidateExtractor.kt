package com.baraa.masroof.lab.bank.experiment

internal typealias CandidateKind = com.baraa.masroof.lab.bank.patterns.CandidateKind
internal typealias TextCandidate = com.baraa.masroof.lab.bank.patterns.TextCandidate
internal typealias ExtractedField = com.baraa.masroof.lab.bank.patterns.TextSpan

// The offline lifecycle now exercises the production text-only implementation.
internal object GenericCandidateExtractor {
    fun extract(body: String) = com.baraa.masroof.lab.bank.patterns.GenericCandidateExtractor.extract(body)
}
