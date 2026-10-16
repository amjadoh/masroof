import unittest
from context_benchmark import hybrid, summary


class ContextBenchmarkTest(unittest.TestCase):
    def test_model_never_promotes_review_invalid_or_partial_to_success(self):
        for status in ("REVIEW_REQUIRED", "INVALID", "PARTIAL", "UNSUPPORTED"):
            family, result_status, _ = hybrid({"status": status, "facts": {"family": "UNKNOWN"}}, "PURCHASE")
            self.assertEqual("PURCHASE", family)
            self.assertEqual(status, result_status)

    def test_otp_and_existing_accepted_results_keep_rules_authority(self):
        for family, status in (("OTP", "NON_FINANCIAL"), ("PURCHASE", "SUCCESS"), ("BALANCE_NOTICE", "NON_FINANCIAL")):
            self.assertEqual((family, status, "rules_authority"),
                             hybrid({"status": status, "facts": {"family": family}}, "TRANSFER_OUT"))

    def test_disagreement_abstains_and_keeps_review(self):
        family, status, _ = hybrid({"status": "REVIEW_REQUIRED", "facts": {"family": "REFUND"}}, "PURCHASE")
        self.assertEqual("UNKNOWN", family)
        self.assertEqual("REVIEW_REQUIRED", status)

    def test_unlabeled_rows_are_timed_but_never_scored_as_correct(self):
        rows = []
        for truth, prediction in (("OTP", "PURCHASE"), ("PURCHASE", "UNKNOWN"), (None, "PURCHASE")):
            rows.append({"expectedFamily": truth, **{m: {"family": prediction, "topFamily": prediction, "elapsedMs": [1.0]} for m in ("rules", "model", "hybrid")}})
        for metric in summary(rows).values():
            self.assertEqual(2, metric["labeled"])
            self.assertEqual(0, metric["correct"])
            self.assertEqual(50, metric["nonUnknownCoveragePct"])
            self.assertEqual(1, metric["financialLabelOnNonfinancialTruth"])


if __name__ == "__main__":
    unittest.main()
