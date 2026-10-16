package com.baraa.masroof.lab.copy.parsing.detector

import com.baraa.masroof.lab.copy.parsing.model.BankDetectionResult

/**
 * Detects which bank (if any) an SMS belongs to.
 *
 * Implementations belong under bank adapters (P4+). P3 defines the contract only.
 */
interface BankDetector {
    fun detect(sender: String, body: String): BankDetectionResult
}
