#!/usr/bin/env python3
"""Checks the replica contract without changing tracked source or fixtures."""
import importlib.util, json, pathlib, subprocess, tempfile, unittest
HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('replica', HERE / 'generate_production_replica.py')
replica = importlib.util.module_from_spec(spec); spec.loader.exec_module(replica)
class ReplicaContractTest(unittest.TestCase):
    def test_source_lock_matches_read_only_production(self):
        lock = json.loads(replica.LOCK.read_text())
        self.assertEqual(lock['files'], replica.inventory())
        self.assertEqual(lock['bootstrapSha256'], replica.digest(replica.BETA/'replica-overlays/bootstrap.json'))
    def test_no_financial_or_screen_body_overlay(self):
        specs = json.loads((replica.BETA/'replica-overlays/bootstrap.json').read_text())
        allowed = {'MainActivity.kt','application/AppContainer.kt','application/locale/AppLocaleContextFactory.kt','application/update/UpdateCheckWorker.kt',
                   'sms/receiver/IncomingSmsReceiver.kt','application/sms/LiveSmsProcessingWorker.kt',
                   'application/sms/HistoricalDerivedRecoveryWorker.kt','application/maintenance/ParsedEventFactsBackfillWorker.kt',
                   'application/sms/ExchangeRateEnrichmentScheduler.kt','application/sms/LiveSmsWorkScheduler.kt',
                   'application/update/UpdateCheckScheduler.kt','data/preferences/AndroidKeystoreGitHubTokenEncryptor.kt',
                   'application/update/ApkInstaller.kt','presentation/navigation/MasroofRoot.kt'}
        self.assertEqual(allowed, {p.removeprefix('kotlin/com/baraa/masroof/') for p in specs})
        self.assertFalse(any('/dashboard/' in p or '/review/' in p or '/domain/' in p or '/room/' in p for p in specs))
    def test_only_value_contracts_remain_from_the_parallel_financial_core(self):
        sources = list((replica.ROOT/'lab/finance-core/src/main').rglob('*.kt'))
        self.assertEqual(['Values.kt'], [p.name for p in sources])
        beta = replica.BETA/'src/main/kotlin'
        self.assertEqual(['BetaDatabase.kt'], [p.name for p in beta.rglob('*.kt')])
        self.assertNotIn('@Database', next(beta.rglob('*.kt')).read_text())
    def test_financial_schema_and_learning_schema_remain_separate(self):
        source = json.loads((replica.ROOT/'app/schemas/com.baraa.masroof.data.room.MasroofDatabase/18.json').read_text())['database']
        sidecar = json.loads((replica.BETA/'replica-schemas/com.baraa.masroof.lab.replica.data.learning.LearningDatabase/1.json').read_text())['database']
        self.assertEqual(14, len(source['entities']))
        self.assertEqual({'engine_interpretation','learning_label'}, {row['tableName'] for row in sidecar['entities']})
    def test_generation_is_deterministic_and_preserves_source(self):
        before = replica.inventory()
        with tempfile.TemporaryDirectory() as temp:
            a,b = pathlib.Path(temp)/'a', pathlib.Path(temp)/'b'
            for out in (a,b):
                subprocess.run(['python3',str(HERE/'generate_production_replica.py'),'--output',str(out)],check=True,capture_output=True)
            def files(out): return {str(p.relative_to(out)):replica.digest(p) for p in out.rglob('*') if p.is_file()}
            self.assertEqual(files(a),files(b))
            self.assertFalse(list(a.rglob('MasroofApplication.kt')))
            self.assertFalse(list((a/'kotlin').rglob('AlJaziraMessageParser.kt')))
            self.assertTrue(list((a/'referenceKotlin').rglob('AlJaziraMessageParser.kt')))
            self.assertTrue(list((a/'kotlin').rglob('AlJaziraBankDetector.kt')))
            self.assertIn('com.baraa.masroof.lab.beta.replica.LabRuntime', next(a.rglob('MainActivity.kt')).read_text())
        self.assertEqual(before,replica.inventory())
if __name__ == '__main__': unittest.main()
