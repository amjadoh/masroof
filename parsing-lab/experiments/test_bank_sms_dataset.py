import unittest
from collections import Counter
from bank_sms_dataset import canonical, grouped, training_eligible


class BankSmsDatasetTest(unittest.TestCase):
    def test_canonical_keeps_context_after_merchant_and_masks_variable_values(self):
        a = "شراء\nبطاقة: 1234\nلدى: First Shop\nالمبلغ المستحق: 500 SAR\nتاريخ الاستحقاق: 2026-10-25"
        b = "شراء\nبطاقة: 9876\nلدى: Second Shop\nالمبلغ المستحق: 900 SAR\nتاريخ الاستحقاق: 2026-11-26"
        self.assertEqual(canonical(a), canonical(b))
        self.assertIn("تاريخ الاستحقاق", canonical(a))
        self.assertNotEqual(canonical(a), canonical("شراء\nبطاقة: 1234\nلدى: First Shop"))

    def test_value_variants_collapse_and_cannot_enter_different_folds(self):
        source = lambda ident, text: {"id": ident, "text": text, "label": "PURCHASE", "source": "test", "labelBasis": "reviewed", "provenance": "test"}
        rows = grouped([source("a", "POS Purchase\nCard: 1234\nAmount: 42 SAR"),
                        source("b", "POS Purchase\nCard: 9876\nAmount: 99 SAR")])
        self.assertEqual(1, len(rows))
        self.assertEqual("b", rows[0]["aliases"][0]["id"])

    def test_similar_semantic_templates_share_a_fold(self):
        make = lambda ident, text: {"id": ident, "text": text, "label": "PURCHASE", "source": "test", "labelBasis": "reviewed", "provenance": "test"}
        rows = grouped([make("a", "شراء عبر نقاط البيع\nبطاقة مدى: 1234\nبمبلغ 42 SAR"),
                        make("b", "شراء من نقاط البيع (Google Pay)\nبطاقة ائتمانية: 9876\nالمبلغ 99 ريال")])
        self.assertEqual(2, len(rows))
        self.assertEqual(rows[0]["group"], rows[1]["group"])
        self.assertEqual(rows[0]["fold"], rows[1]["fold"])

    def test_conflicting_labels_for_value_only_variants_fail(self):
        rows = [{"id": "a", "text": "Amount: 42 SAR", "label": "PURCHASE"},
                {"id": "b", "text": "Amount: 99 SAR", "label": "REFUND"}]
        with self.assertRaises(ValueError):
            grouped(rows)

    def test_archived_source_alias_is_eligible_but_fixture_only_control_is_not(self):
        self.assertFalse(training_eligible({"id": "fixture:a", "aliases": []}))
        self.assertTrue(training_eligible({"id": "fixture:a", "aliases": [{"id": "reference:original"}]}))


if __name__ == "__main__":
    unittest.main()
