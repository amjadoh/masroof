# Optional offline learning tools

These Python tools provide source-example inventory, template grouping, human-provenance training gates, model evaluation and local measurement helpers. They are not a parser runtime, do not post money, and are not enabled in Beta. Existing human training annotations in `setfit_training.json` are retained.

The copied legacy parser, its paired evidence comparison runner, reports and obsolete reproduction instructions have been removed. The canonical source corpus stays under `app/src/test/resources/testdata/bank_aljazira`; its expectations are unchanged. No old-model accuracy claim authorizes Beta financial use.

Run `python -m unittest discover -s lab/experiments -p 'test_*.py'`. No model download or training is part of CI. Promotion/export requires the plan's independent holdout, Android measurement and explicit opt-in gates.
