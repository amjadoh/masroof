# CI and release

`ci.yml` runs on PRs and main. It verifies the latest stable Android image, script routing, Android unit tests, pure JVM modules, model reproducibility, lint/detekt and the complete application instrumentation suite in two disjoint shards. The shared `ui-kit` remains covered.

Stateful recovery uses separate isolated emulators for production process-death and actual APK update. `run-lifecycle-suite.sh production` runs M13; `upgrade` builds baseline b76c1afc and the promoted app, then runs `scripts/app-apk-upgrade.sh` without uninstalling or clearing between its before/after phases. Stateful test packages are excluded from orchestrated smoke to preserve their databases.

The former separate Lab CI workflow and emulator jobs are removed. All transferred feature tests run as part of the real app. Release verifies green CI and builds the APK; it does not rerun the full unit suite.
