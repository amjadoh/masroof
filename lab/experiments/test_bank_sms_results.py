import unittest
from unittest.mock import patch
from bank_sms_results import aggregate


class BankSmsResultsTest(unittest.TestCase):
    def test_reused_prediction_from_another_fold_is_rejected(self):
        manifest = {"rows": [{"id": "a", "fold": 0, "group": "g", "trainingEligible": True}]}
        row = {"id": "a", "expected": "PURCHASE"}
        first = {"rows": [row], "training": {"classes": ["PURCHASE"]}, "splitAudit": {}}
        with patch("bank_sms_results.read_json", side_effect=[manifest, first, first]):
            with self.assertRaises(AssertionError):
                aggregate("unused")

    def test_missing_held_out_predictions_are_rejected(self):
        manifest = {"rows": [{"id": "a", "fold": 0}]}
        empty = {"rows": [], "training": {}, "splitAudit": {}}
        with patch("bank_sms_results.read_json", side_effect=[manifest, empty, empty, empty]):
            with self.assertRaises(AssertionError):
                aggregate("unused")


if __name__ == "__main__":
    unittest.main()
