# Masroof Lab: production UI, new interpretation engine

Enter through the existing APK: Settings → Developer settings → Masroof 2. No separate APK or upgrade-host installation is needed.

The build generates the existing app's UI, ViewModels, application workflows and Room schema into a separate package. Only reviewed bootstrap overlays change wiring. Financial posting, ownership, review, dashboards, backup and setup remain the original implementation. The original `app/` and `ui-kit/` sources are read-only. The old parallel Lab UI and financial calculators have been removed.

- Financial data: `lab_replica_masroof.db`, the production v18 schema with fourteen tables.
- Learning/provenance: `lab_replica_learning.db`, two advisory tables; no duplicated ledger.
- Analytics: `lab_replica_analytics.db`, five advisory tables; purchase/commitment forecasts and alerts. The pure engine lives inside `lab/ml-assist`, separate from SMS classification. See [the implementation and data contract](ANALYTICS_IMPLEMENTATION_AR.md).
- Model: scoped `files/lab_replica/learning/family-model.json`, seeded from the reproducible trained asset.
- Preferences, files, work names, keystore alias and provider are scoped to Lab. Reset affects this namespace only. Old `masroof_beta*.db` files are preserved and never migrated or opened.

`BankEngine` supplies validated parsed facts through the original bank-adapter contract. Known rules run first. A local multinomial Naive Bayes model can assist new titles only for evaluated enabled families (currently incoming transfers). Explicit unambiguous review decisions teach templates for later messages and trigger one batch refresh of unresolved unknowns. ML never supplies financial amounts, ownership or transaction approval. Ambiguous/conflicting/unsupported evidence remains reviewable.

Build: `./gradlew :app:assembleDebug`. Verify: `python3 lab/beta/tools/test_production_replica.py`, `./gradlew testDebugUnitTest :lab:bank-engine:test :lab:finance-core:test`, and native/lifecycle CI. Reproduce training: `python3 lab/bank-engine/tools/train_family_model.py --check` with JDK 17 and the Android SDK configured.

Source changes require reviewing the bootstrap allowlist and explicitly updating `PRODUCTION_REPLICA_SOURCES.json` with `python3 lab/beta/tools/generate_production_replica.py --lock`. There are no hand-maintained screen copies. The test-only reference parser never ships in the runtime. `finance-core` retains only BankEngine money/bank value contracts; the original production financial workflows own all calculations.

`upgrade-host` is CI-only: two synthetic signed versions verify update preservation and the original encrypted backup service. Historical M0 host isolation tests retain a tiny deprecated diagnostic facade and a retired schema path marker; neither implements a second financial engine or product screen.

See [the HTML implementation and acceptance report](beta/PRODUCTION_REPLICA_REPORT_AR.html), [engine differences](beta/ENGINE_PARITY_DIFFERENCES.json), and [ML evaluation](beta/ML_SEED_EVALUATION.json). Native evidence is from an Android 16 emulator, not a user's phone.
