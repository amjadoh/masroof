"""Activation policy regression tests; no corpus files or generated artifacts are written."""
import copy
import unittest

from train_family_model import train


class ActivationPolicyTest(unittest.TestCase):
    def corpus(self, count=8):
        rows = []
        partitions = {}
        for name, split in [('train', 'train')] + [(f'validation-{i}', 'validation') for i in range(count)] + [('test', 'test')]:
            rows.append(dict(id=name, template=name, family='TRANSFER_IN', features={'w:transfer': 1},
                             runtimeEligible=True, expectedPassesValidation=True,
                             candidateOutcomes={'TRANSFER_IN': dict(validationAccepted=True, assembly='Assembled')}))
            partitions[name] = split
        return dict(rows=rows, rejected=[], bankId='BANK_ALJAZIRA', cleaningVersion='clean', featuresVersion='features'), partitions

    def test_requires_eight_accepted_fallback_templates_and_financial_validation(self):
        for count in [0, 1, 7]:
            source, frozen = self.corpus(count)
            self.assertEqual([], train(source, frozen)[0]['enabledFamilies'])
        source, frozen = self.corpus()
        self.assertEqual(['TRANSFER_IN'], train(source, frozen)[0]['enabledFamilies'])
        for field in ['runtimeEligible', 'expectedPassesValidation']:
            invalid = copy.deepcopy(source)
            for row in invalid['rows']:
                row[field] = False
            self.assertEqual([], train(invalid, frozen)[0]['enabledFamilies'])

    def test_duplicate_templates_do_not_inflate_support(self):
        source, frozen = self.corpus()
        for row in source['rows']:
            if row['id'].startswith('validation'):
                row['template'] = 'same-template'
        self.assertEqual([], train(source, frozen)[0]['enabledFamilies'])

    def test_wrong_accepted_validation_disables_family_and_wrong_final_test_blocks_release(self):
        source, frozen = self.corpus()
        source['rows'].append(dict(source['rows'][1], id='negative', template='negative', family='OTP'))
        frozen['negative'] = 'validation'
        self.assertEqual([], train(source, frozen)[0]['enabledFamilies'])
        source, frozen = self.corpus()
        source['rows'][-1]['family'] = 'OTP'
        with self.assertRaisesRegex(ValueError, 'Unsafe final runtime holdout'):
            train(source, frozen)

    def test_test_features_never_change_vocabulary_or_training_counts(self):
        source, frozen = self.corpus()
        original, _ = train(source, frozen)
        source['rows'][-1]['features'] = {'w:unseen': 1}
        changed, _ = train(source, frozen)
        for key in ['vocabulary', 'counts', 'documents', 'enabledFamilies']:
            self.assertEqual(original[key], changed[key])


if __name__ == '__main__':
    unittest.main()
