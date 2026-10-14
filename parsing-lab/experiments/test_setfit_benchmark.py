import unittest
from pathlib import Path
from setfit_benchmark import audit_splits, choose_threshold, corpus_rows, guarded_hybrid


class SetFitBenchmarkTest(unittest.TestCase):
    def test_number_changes_do_not_disguise_train_test_leakage(self):
        with self.assertRaises(ValueError):
            audit_splits({"train": [{"id": "a", "text": "Amount 123.00 SAR card 4567"}],
                          "test": [{"id": "b", "text": "Amount 789.00 SAR card 0123"}]})

    def test_near_duplicate_templates_are_rejected(self):
        with self.assertRaises(ValueError):
            audit_splits({"train": [{"id": "a", "text": "Your card purchase at the bookstore was successful today"}],
                          "test": [{"id": "b", "text": "Your card purchase at the bookstore was successful now"}]})

    def test_validation_alone_selects_abstention_threshold(self):
        chosen = choose_threshold([[.51, .49], [.1, .9]], ["PURCHASE", "OTP"], ["UNKNOWN", "OTP"])
        self.assertEqual(2, chosen["correct"])
        self.assertEqual(.75, chosen["threshold"])

    def test_model_cannot_overrule_context_conflict_or_promote_review(self):
        outcome = {"status": "REVIEW_REQUIRED", "facts": {"family": "UNKNOWN"},
                   "trace": ["candidate:shared.context.statement", "ambiguous_classification"], "normalizedText": "a message"}
        self.assertEqual(("UNKNOWN", "REVIEW_REQUIRED", "context_conflict_retained"), guarded_hybrid(outcome, "PURCHASE"))
        outcome["trace"] = ["تعارض اتجاه العملية"]
        self.assertEqual("UNKNOWN", guarded_hybrid(outcome, "TRANSFER_IN")[0])
        outcome["trace"] = ["unrecognized format"]
        self.assertEqual(("PURCHASE", "REVIEW_REQUIRED", "semantic_suggestion_review_retained"), guarded_hybrid(outcome, "PURCHASE"))
        outcome["normalizedText"] = ""
        self.assertEqual("UNKNOWN", guarded_hybrid(outcome, "PURCHASE")[0])

    def test_authored_training_and_validation_cover_same_classes_without_duplicates(self):
        splits = corpus_rows(Path(__file__).with_name("setfit_training.json"))
        self.assertEqual(78, len(splits["train"]))
        self.assertEqual(26, len(splits["validation"]))
        self.assertEqual({r["label"] for r in splits["train"]}, {r["label"] for r in splits["validation"]})
        audit_splits(splits)


if __name__ == "__main__":
    unittest.main()
