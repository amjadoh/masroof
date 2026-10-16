import unittest
from evidence_comparison import differences, summarize, same, prepare_tuned, write, read
import tempfile
from pathlib import Path


class EvidenceComparisonTest(unittest.TestCase):
    def test_tuning_changes_only_knowledge_and_preserves_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = [{"id": "a", "body": "sample", "expected": {"status": "SUCCESS", "facts": {"family": "PURCHASE"}}}]
            knowledge = {"version": 1, "rules": [{"concept": "field.source", "phrases": ["from account"]}, {"concept": "field.card", "phrases": ["card"]}]}
            write(root / "cases.json", original);write(root / "base.json", knowledge)
            prepare_tuned(root, root / "base.json")
            tuned = read(root / "cases-tuned.json")
            self.assertEqual(original, read(root / "cases.json"))
            self.assertEqual(original[0]["expected"], tuned[0]["expected"])
            self.assertEqual(2, tuned[0]["knowledge"]["version"])
            self.assertEqual(knowledge["rules"][1], tuned[0]["knowledge"]["rules"][1])
    def test_absence_and_numeric_equivalence(self):
        reference = {"status": "SUCCESS", "facts": {"amount": "10.00"}, "absentFields": ["card"]}
        self.assertEqual(["card"], differences(reference, {"status": "SUCCESS", "facts": {"amount": "10", "card": "2026"}}, "fixture"))
        self.assertEqual([], differences({"status": "PARTIAL", "facts": {"family": "PURCHASE"}}, {"status": "REVIEW_REQUIRED", "facts": {"family": "PURCHASE", "card": "2026"}}, "family_only"))
        self.assertTrue(same("occurredAtLocal", "2026-10-01T09:00:00", "2026-10-01T09:00"))
        with self.assertRaises(AssertionError): differences({"status": "SUCCESS", "facts": {"card": "1234"}, "absentFields": ["card"]}, {"status": "SUCCESS"}, "fixture")

    def test_partial_reference_is_not_counted_as_complete_event(self):
        outcome = {"status": "SUCCESS", "facts": {"family": "PURCHASE", "amount": "1"}}
        row = {"id": "a", "expected": {"status": "PARTIAL", "facts": {"family": "PURCHASE"}}, "shared": outcome,
               "evidence": {"outcome": outcome}, "sharedMs": [1, 2], "evidenceMs": [2, 3], "sharedFirstMs": 10, "evidenceFirstMs": 11}
        manifest = {"rows": [{"id": "a", "group": "g", "scope": "family_only", "sourceBacked": True}]}
        result = summarize([row], manifest)
        self.assertEqual(1, result["metrics"]["all"]["shared"]["familyCorrect"])
        self.assertEqual(0, result["metrics"]["all"]["shared"]["annotatedTotal"])
        self.assertNotIn("facts", result["rows"][0]["modes"]["shared"])
        with self.assertRaises(AssertionError): summarize([row, row], manifest)


if __name__ == "__main__": unittest.main()
