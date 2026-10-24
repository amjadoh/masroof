#!/usr/bin/env python3
import copy
import unittest
import train_family_model as training


class TrainingContractTest(unittest.TestCase):
    def test_new_splits_group_templates_without_using_labels_and_preserve_frozen_test(self):
        rows = [self.row('a'), self.row('b'), self.row('c', 'other')]
        exported = dict(rows=rows, rejected=[])
        original = training.freeze_splits(exported, {'a': 'test'})
        self.assertEqual('test', original['partitions']['b'])
        changed = copy.deepcopy(exported)
        changed['rows'][0]['family'] = 'OTP'
        self.assertEqual(original, training.freeze_splits(changed, {'a': 'test'}))
    def row(self, identity, template='same', family='PURCHASE'):
        return dict(id=identity, template=template, family=family, features={'w:purchase': 1})

    def test_duplicate_templates_cannot_cross_splits(self):
        parts, conflicts = training.partition_rows([self.row('a'), self.row('b')], {'a': 'train', 'b': 'test'})
        self.assertEqual([], parts['train'])
        self.assertEqual(['b'], [r['id'] for r in parts['test']])
        self.assertEqual([], conflicts)

    def test_conflicting_labels_are_quarantined_before_any_split(self):
        parts, conflicts = training.partition_rows([self.row('a'), self.row('b', family='OTP')], {'a': 'train', 'b': 'test'})
        self.assertFalse(any(parts.values()))
        self.assertEqual(['a', 'b'], conflicts[0]['sourceIds'])

    def test_duplicates_and_unreviewed_new_samples_fail(self):
        for rows, frozen in (([self.row('a')] * 2, {'a': 'train'}), ([self.row('new')], {})):
            with self.assertRaises(ValueError):
                training.partition_rows(rows, frozen)

    def test_test_labels_cannot_select_enabled_families(self):
        rows = [self.row('a', 'train', 'TRANSFER_IN'), self.row('b', 'validation', 'TRANSFER_IN'),
                self.row('c', 'test', 'TRANSFER_IN')]
        exported = dict(rows=rows, rejected=[], cleaningVersion='test', featuresVersion='test', bankId='BANK_TEST')
        frozen = {'a': 'train', 'b': 'validation', 'c': 'test'}
        before, _ = training.train(exported, frozen)
        changed = copy.deepcopy(exported)
        changed['rows'][-1]['family'] = 'OTP'
        after, _ = training.train(changed, frozen)
        self.assertEqual(before['enabledFamilies'], after['enabledFamilies'])
        self.assertEqual(before['counts'], after['counts'])
        self.assertEqual(before['policy'], after['policy'])


if __name__ == '__main__':
    unittest.main()
