# Masroof | مصروف

Masroof is a local-first Android personal finance app. It turns supported bank SMS into reviewed financial records, account and card summaries, commitments, and spending insights.

[العربية](README_AR.md)

## Features

- Historical SMS import for selected banks and receipt-date ranges, plus durable live processing with duplicate protection and retry recovery.
- A rule-based bank engine with validation and explicit ownership confirmation. AlJazira uses bundled role definitions; six STC templates require explicit financial approval.
- Local machine learning for eligible message families and learning from explicit user corrections. It never invents amounts, ownership, or financial approval.
- Salary-period dashboards, transaction details, a review inbox, accounts, cards, credit facilities, loans, and commitments.
- Purchase forecasts, recurring-payment suggestions, historical spending, and a unified notification center. Phone alerts are opt-in.
- Arabic/RTL and English, shared design components, and light/dark/system appearance.
- Encrypted backup and recovery, including analytics and message patterns; financial backups must use schema v18.

## Project

| Module | Responsibility |
| --- | --- |
| `app` | Android UI, application workflows, SMS intake, persistence and recovery |
| `bank-engine` | Pure Kotlin bank-scoped patterns, interpretation and extraction evidence |
| `finance-core` | Pure financial value models, periods, identities, classification, matching and assembly |
| `ml-assist` | Pure Kotlin family assistance, forecasting and recurring-pattern detection |
| `ui-kit` | Shared theme, fonts, components and charts |

The tested engine and analytics are integrated directly into the app. There is no separate Lab screen, database namespace or generated copy of the application. Existing internal Kotlin package names are retained where that avoids unnecessary changes.

## Build and verify

Use JDK 21 and an Android SDK configured in `local.properties` (`sdk.dir=...`).

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests '*MessagePatternsWorkflowTest'
./gradlew lintDebug detekt
python3 ml-assist/tools/train_family_model.py --check
python3 ml-assist/tools/test_training.py
```

Tests export the exact Kotlin-cleaned training features to `app/build/model-training/features.json`; the training check consumes that export. Full unit suites run in PR/main CI; run focused tests locally. Device smoke and stateful recovery/update journeys run separately in CI. Release checks green CI before building the APK.

## Data and upgrades

The production database remains `masroof.db`, schema version 18. Updates preserve financial records, SMS identities, ownership, corrections, commitments, settings within schema v18. Older database and financial backup schemas are rejected without migration or destructive fallback. Optional features use separate `learning.db` and `analytics.db` stores. Old Lab data is not imported or automatically erased. Engine/model refreshes target unresolved evidence and preserve posted transactions and manual decisions.

SMS access is requested for import and live intake. Financial data is stored locally; automatic Android cloud backup is disabled. Network access is used by existing exchange-rate and app-update features. Exported backups are password protected. Forecasts are advisory and never post financial transactions.

See [architecture](docs/ARCHITECTURE.md), [analytics](docs/ANALYTICS.md), [design system](docs/DESIGN_SYSTEM.md), [CI](docs/CI.md), [releases](docs/RELEASE.md), and [agent notes](AGENTS.md).

[Cutover file inventory](docs/PRODUCTION_CUTOVER.md).
