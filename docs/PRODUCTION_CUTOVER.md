# النقل إلى مصروف الفعلي

مصروف الفعلي هو التطبيق الوحيد. نُقلت إضافات المختبر المعتمدة إلى مصدره، وأزيلت نسخة التطبيق المولدة ومسارات دخول المختبر والمحرك السابق. بيانات المختبر التجريبية لا تُستورد إلى البيانات الفعلية.

## الواجهة والبيانات

- بقيت شاشات الفعلي والثيم في `ui-kit`؛ أضيفت بطاقة التوقعات والتحليل ومصدر التفسير ومركز التنبيهات الموحد من المختبر.
- التغييرات الوظيفية طُبقت على ملفات الفعلي مباشرة؛ لا يوجد استيراد لشاشات من نسخة مولدة أو اعتماد Android على المختبر.
- بقيت `masroof.db` بإصدار 18 ومخططها الحالي، ومعرّف التطبيق والإعدادات ومفتاح التشفير وهويات العاملين الأصلية.
- تحديث القواعد أو النموذج لا يعيد تفسير حركة مسجلة أو رسالة لها تصحيح أو قرار يدوي. التعلم يساعد في العائلة فقط، والاعتماد المالي يمر بالتحقق والملكية والمصالحة.
- ملفات المستخدم المحلية الستة التي كانت غير متتبعة حُفظت دون تعديل تحت `.local-preserved/lab/`، خارج البناء وGit.

## الوحدات المنقولة

نُقلت هذه الوحدات من `lab/` إلى الجذر، مع إبقاء أسماء حزم Kotlin الداخلية لتقليل التغيير. لا تعني كلمة `lab` داخل الحزمة وجود تطبيق ثانٍ.

### `bank-engine/`

- `bank-engine/build.gradle.kts`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/Interpretation.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/NetworkRules.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/RuleSchema.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/DateGrammar.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/DetailExtractor.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/FamilyAssist.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/FamilyTextPreparation.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/MoneyGrammar.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/ObservationExtractor.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/RoleLineReader.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/RuleInterpreter.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/SourceText.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/interpretation/TransferNetworkResolver.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/rules/AlJaziraRuleProfile.kt`
- `bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/rules/RuleLibrary.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/IndependentRuleInterpreterTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/InterpretationContractTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/M8StatementAuthorityTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/MessageLayoutCoverageTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/ModuleBoundaryTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/ObservationRulesTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/RoleBoundDetailsTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/RuleSchemaTest.kt`
- `bank-engine/src/test/kotlin/com/baraa/masroof/engine/bank/TransferNetworkTest.kt`

### `finance-core/`

- `finance-core/build.gradle.kts`
- `finance-core/src/main/kotlin/com/baraa/masroof/finance/value/Values.kt`
- `finance-core/src/test/kotlin/com/baraa/masroof/finance/ValueContractTest.kt`

### `ml-assist/`

- `ml-assist/.gitignore`
- `ml-assist/ML_SEED_EVALUATION.json`
- `ml-assist/build.gradle.kts`
- `ml-assist/src/main/kotlin/com/baraa/masroof/analytics/AnalyticsData.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/analytics/PurchaseForecaster.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/analytics/RecurringDetector.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/ml/catalog/ModelManifest.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/ml/family/FamilyClassifier.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/ml/family/FamilyContracts.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/ml/family/FamilyInputCleaner.kt`
- `ml-assist/src/main/kotlin/com/baraa/masroof/ml/family/FamilyModel.kt`
- `ml-assist/src/main/resources/ml/family-seed.json`
- `ml-assist/src/test/kotlin/com/baraa/masroof/analytics/AnalyticsEngineTest.kt`
- `ml-assist/src/test/kotlin/com/baraa/masroof/ml/family/FamilyClassifierTest.kt`
- `ml-assist/src/test/kotlin/com/baraa/masroof/ml/family/FamilyInputCleanerTest.kt`
- `ml-assist/tools/baseline-evaluation.json`
- `ml-assist/tools/corpus-splits.json`
- `ml-assist/tools/test_training.py`
- `ml-assist/tools/train_family_model.py`

## ملفات التشغيل الجديدة داخل الفعلي

كل المسارات التالية هي ملفات فعلية داخل وحدة `app`، وليست روابط إلى ملفات المختبر.

- `app/schemas/com.baraa.masroof.data.analytics.AnalyticsDatabase/1.json`
- `app/schemas/com.baraa.masroof.data.learning.LearningDatabase/1.json`
- `app/src/main/kotlin/com/baraa/masroof/application/analytics/AnalysisAlertPolicy.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/analytics/AnalyticsModels.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/analytics/AnalyticsWorker.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/analytics/FinancialAnalysisReader.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/analytics/AnalyticsWorkflow.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/interpretation/InterpretationProvenanceWorkflow.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/interpretation/FamilyAssistBridge.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/interpretation/BankInterpretationService.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/interpretation/ModelRefreshWorker.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/maintenance/RetiredWorkCleanup.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/InterpretationDraftMapper.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/NewEngineSmsAdapter.kt`
- `app/src/main/kotlin/com/baraa/masroof/data/analytics/AnalyticsDatabase.kt`
- `app/src/main/kotlin/com/baraa/masroof/data/learning/FamilyModelCodec.kt`
- `app/src/main/kotlin/com/baraa/masroof/data/learning/LearningStore.kt`
- `app/src/main/kotlin/com/baraa/masroof/data/learning/LearningDatabase.kt`
- `app/src/main/kotlin/com/baraa/masroof/data/room/PreservingOpenHelperFactory.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/analytics/AnalyticsReadViews.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/analytics/AnalyticsScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/interpretation/InterpretationProvenanceCard.kt`
- `app/src/main/res/values-en/analytics.xml`
- `app/src/main/res/values-en/replica.xml`
- `app/src/main/res/values/analytics.xml`
- `app/src/main/res/values/replica.xml`

## ملفات التشغيل المعدلة


- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/baraa/masroof/MainActivity.kt`
- `app/src/main/kotlin/com/baraa/masroof/MasroofApplication.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/AppContainer.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/backup/BackupPackageCodec.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/backup/BackupPackageFormat.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/backup/DatabaseBackupService.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/backup/DatabaseRestoreRecovery.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/dashboard/DashboardProjectionBuilder.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/dashboard/DashboardService.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/dashboard/TransactionSmsEvidenceLoader.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/review/ReviewDetailLoader.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/review/ReviewWorkflowService.kt`
- `app/src/main/kotlin/com/baraa/masroof/application/sms/HistoricalSmsScanner.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/common/MasroofLayoutComponents.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/dashboard/DashboardModels.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/dashboard/DashboardScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/dashboard/DashboardViewModelFactory.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/dashboard/TransactionDetailScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/navigation/AppNavigation.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/navigation/MasroofRoot.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/navigation/SettingsDestination.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/notification/NotificationCenterScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/review/ReviewModels.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/review/ReviewReasonLabels.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/review/ReviewScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/review/ReviewViewModel.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/review/ReviewViewModelFactory.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsAboutScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsCommitmentEditorScreen.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsScreen.kt`
- `settings.gradle.kts`

## المحذوف

حُذف مجلد `lab/` كاملًا، بما فيه `beta` و`upgrade-host` وتوليد نسخة التطبيق وتقارير المختبر القديمة. الملفات المفيدة واختباراتها نُقلت أولًا. وحُذف المحرك السابق من الفعلي، بما فيه ملفات التصنيف والاستخراج والواجهات التالية:

- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraLoanTypeMapper.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraMessageParser.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraParsingPipeline.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraSalaryIncomeHeuristics.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraSmsAdapter.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/CreditCardMessageHeuristics.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/CreditCardStatementHeuristics.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/classification/AlJaziraClassificationResolver.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/classification/AlJaziraClassificationRule.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/classification/AlJaziraMessageClassifier.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/AccountExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/AmountExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/BalanceExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/BillerExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/CardExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/CounterpartyExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/DateTimeExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/DueDateExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/InternationalPurchaseFactsExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/LoanLabelExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/MerchantExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/MoneyTokens.kt`
- `app/src/main/kotlin/com/baraa/masroof/bank/aljazira/extraction/ReferenceExtractor.kt`
- `app/src/main/kotlin/com/baraa/masroof/parsing/parser/BankMessageParser.kt`
- `app/src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsDeveloperScreen.kt`
- `app/src/main/res/values-en/beta_bridge_strings.xml`
- `app/src/main/res/values-en/developer_settings_strings.xml`
- `app/src/main/res/values/beta_bridge_strings.xml`
- `app/src/main/res/values/developer_settings_strings.xml`

بقي `AlJaziraBankDetector.kt` لاكتشاف المُرسل فقط؛ ليس محللًا أو محرك تصنيف قديمًا. أُزيلت اختبارات تفاصيل التنفيذ المحذوفة، وبقيت اختبارات الرسائل والسجل المالي واختبارات الانحدار مرتبطة بالمحرك الجديد. لا توجد نسخة اختبارية من المحرك القديم.

## الاختبارات والتشغيل المستمر

- اختبارات الميزات المنقولة: `app/src/test/kotlin/com/baraa/masroof/integration/` و`app/src/androidTest/kotlin/com/baraa/masroof/integration/`. أُزيلت أسماء Lab/Beta من مسارات المصدر والحزم؛ كلها تستهدف التطبيق الفعلي. مفاتيح النسخ الاحتياطي وهويات العمل القديمة بقيت للتوافق.
- أدوات العزل الاصطناعي في `app/src/androidTest/kotlin/com/baraa/masroof/testsupport/` لا تُشحن في APK التطبيق.
- اختبار حفظ الحركات والقرارات: `app/src/test/kotlin/com/baraa/masroof/application/interpretation/ProductionEnginePreservationTest.kt`.
- أُزيل اختبار تحديث APK من إصدار سابق (`AppUpgradePreservationTest` و`scripts/app-apk-upgrade.sh`) مع الانتقال إلى Java 21، لأنه كان يبني الإصدار القديم بـ JDK 17.
- حُذفت `.github/workflows/lab.yml` وأدوات تحديث/تشغيل المختبر. تعمل اختبارات الميزات داخل CI الفعلي. بقي `release.yml` دون إضافة اختبارات الوحدة إليه.

## التوثيق

حُدثت `README.md` و`README_AR.md` و`AGENTS.md` وملفات `ARCHITECTURE.md` و`PARSING_SPEC.md` و`MODERN_STACK.md` و`CI.md`. نُقل توثيق التحليل إلى `docs/ANALYTICS.md`. أزيلت `docs/beta/` و`docs/ui-parity/` وتقارير المراحل القديمة `docs/M*.md` و`docs/REWRITE_DECISIONS.md` بعد نقل المعلومات الحالية إلى المراجع المعتمدة.

## بقية ملفات البناء والاختبار المعدلة أو المضافة

- `.github/scripts/run-instrumentation-smoke.sh`
- `.github/scripts/run-lifecycle-suite.sh`
- `.github/workflows/ci.yml`
- `.gitignore`
- `app/src/androidTest/kotlin/com/baraa/masroof/instrumentation/LiveRetryRestartTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/instrumentation/SmsPersistenceRestartTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/analytics/AnalyticsJourneyTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/learning/ReplicaMachineLearningTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/replica/ReplicaFinancialParityTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/replica/ReplicaSetupJourneyTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/replica/ReplicaStorageRecoveryTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/integration/replica/ReportedMessagesJourneyTest.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/testsupport/EngineFixtureParser.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/testsupport/FinancialHistoryFixture.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/testsupport/TestRuntime.kt`
- `app/src/androidTest/kotlin/com/baraa/masroof/testsupport/TestStorageContext.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/baseline/M0ProductionBaselineTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/dashboard/MadaGooglePayPosReproTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/ingestion/CaptureBankSmsUseCaseTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/ingestion/ProcessRawSmsUseCaseTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/ingestion/ProcessStoredSmsUseCaseTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/maintenance/ParsedEventFactsBackfillCoordinatorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/maintenance/StoredSmsReprocessorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/sms/HistoricalSmsBatchProcessorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/sms/HistoricalSmsScannerTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/sms/LiveSmsProcessingHarness.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/transaction/IntraBankSelfTransferIngestionTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/transaction/IntraBankSelfTransferReconciliationTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraFixtureParserTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraParserRegressionTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/ParsedEventDetailsFactsCharacterizationTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/contract/BankSmsAdapterContractTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/data/repository/RoomRawSmsRepositoryTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/domain/loan/FinancingInstallmentAssemblyTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integrity/IntegrityLedger.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/analytics/AnalyticsPersistenceTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/analytics/AnalyticsReadModelsTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/bank/DeviceReportedMessagesTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/bank/NewEngineFixtureTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/bank/NewEngineGateTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/learning/FamilyFeatureExportTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/learning/FamilyLearningTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/learning/InterpretationProvenanceTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/integration/learning/LearningPersistenceTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/parsing/fixtures/AlJaziraFixtureLoader.kt`
- `app/src/test/kotlin/com/baraa/masroof/parsing/fixtures/AlJaziraFixtureParseHarness.kt`
- `app/src/test/kotlin/com/baraa/masroof/presentation/navigation/SettingsDestinationNavigationTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/testsupport/AlJaziraFixtureInbox.kt`
- `app/src/test/kotlin/com/baraa/masroof/testsupport/DashboardLedgerWorld.kt`
- `app/src/test/kotlin/com/baraa/masroof/testsupport/EngineFixtureParser.kt`
- `app/src/test/kotlin/com/baraa/masroof/testsupport/TestStorageContext.kt`
- `app/src/test/resources/testdata/approved-engine-differences.json`
- `app/src/test/resources/testdata/engine/device-reported/device_google_pay.json`
- `app/src/test/resources/testdata/engine/device-reported/device_instant_transfer.json`
- `app/src/test/resources/testdata/engine/device-reported/device_water_bill.json`
- `app/src/test/resources/testdata/engine/reported/reported_01.json`
- `app/src/test/resources/testdata/engine/reported/reported_02.json`
- `app/src/test/resources/testdata/engine/reported/reported_03.json`
- `app/src/test/resources/testdata/engine/reported/reported_04.json`
- `app/src/test/resources/testdata/engine/reported/reported_05.json`
- `app/src/test/resources/testdata/engine/supported/collision_loan_types.json`
- `app/src/test/resources/testdata/engine/supported/collision_loyalty_transfer.json`
- `app/src/test/resources/testdata/engine/supported/supported_fee_title.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_auto.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_generic.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_house.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_mortgage.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_personal.json`
- `app/src/test/resources/testdata/engine/supported/supported_loan_vehicle.json`
- `app/src/test/resources/testdata/engine/supported/supported_loyalty.json`
- `app/src/test/resources/testdata/engine/supported/supported_transfer_other_bank_same_suffix.json`
- `app/src/test/resources/testdata/engine/supported/supported_transfer_same_bank_other_in.json`
- `app/src/test/resources/testdata/engine/supported/supported_transfer_same_bank_other_out.json`
- `app/src/test/resources/testdata/engine/supported/supported_transfer_same_bank_own.json`
- `app/src/test/resources/testdata/engine/supported/supported_withdrawal_en.json`
- `build-logic/pure-jvm-boundaries.gradle.kts`
- `scripts/test-instrumentation-smoke.py`
- `scripts/test-lifecycle-suite.py`

## بقية الملفات المحذوفة خارج مجلد المختبر

- `app/src/androidTest/kotlin/com/baraa/masroof/instrumentation/BetaDeveloperEntrySmokeTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/baseline/BetaBridgeEntryPointTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/application/baseline/BetaProductionIsolationTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraLoanTypeMapperTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/CreditCardMessageHeuristicsTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/classification/AlJaziraClassificationResolverTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/classification/AlJaziraMessageClassifierTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/extraction/CardExtractorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/extraction/DueDateExtractorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/extraction/InternationalPurchaseFactsExtractorTest.kt`
- `app/src/test/kotlin/com/baraa/masroof/bank/aljazira/extraction/MoneyTokensTest.kt`
- `docs/M3_BETA_STORAGE_SAFETY.md`
- `docs/M4_BETA_BANK_SCOPE.md`
- `docs/M5_BETA_ASSET_OWNERSHIP.md`
- `docs/M6_BETA_ENGINE_ACTIVATION.md`
- `docs/M6_INDEPENDENT_ENGINE.md`
- `docs/M6_MESSAGE_COVERAGE.md`
- `docs/M6_ROLE_BOUND_DETAILS.md`
- `docs/M6_TRANSFERS_NETWORK_OBSERVATIONS.md`
- `docs/M7_BETA_TRUSTED_CORRECTIONS.md`
- `docs/M8_BETA_FINANCIAL_CUTOVER.md`
- `docs/M9_BETA_HOME_PARITY.md`
- `docs/beta/DATA_MIGRATION.md`
- `docs/beta/M0_CLEAN_BETA_BASELINE.json`
- `docs/beta/M0_CLEAN_BETA_BASELINE.md`
- `docs/beta/M10_REVIEW_CORRECTIONS.md`
- `docs/beta/M10_SETTINGS_RECOVERY.md`
- `docs/beta/M11_ENCRYPTED_RECOVERY.md`
- `docs/beta/M12_SIGNED_UPDATE_REHEARSAL.md`
- `docs/beta/M1_BUILD_COMPATIBILITY.md`
- `docs/beta/M2_DASHBOARD_READ_CONTRACT.md`
- `docs/beta/M2_DOMAIN_CONTRACTS.md`
- `docs/beta/M2_M6_INTERPRETATION_BRIDGE.md`
- `docs/beta/M4_BANK_SELECTION.md`
- `docs/beta/M5_DISCOVERY_ONBOARDING.md`
- `docs/beta/M5_MANUAL_ASSET_SETUP.md`
- `docs/beta/M6_ACCEPTANCE.md`
- `docs/beta/M6_NETWORK_IDENTITY.md`
- `docs/beta/M6_SHARED_RULE_PARITY.md`
- `docs/beta/M7_DURABLE_EVIDENCE.md`
- `docs/beta/M7_FINANCIAL_ADOPTION.md`
- `docs/beta/M7_HISTORY_IMPORT.md`
- `docs/beta/M8_CORRECTION_OVERLAYS.md`
- `docs/beta/M8_DURABLE_CORRECTIONS.md`
- `docs/beta/M8_DURABLE_LEDGER.md`
- `docs/beta/M8_FINANCIAL_CLASSIFICATION.md`
- `docs/beta/M8_FINANCIAL_PARITY.md`
- `docs/beta/M8_FX_ENRICHMENT_PARITY.md`
- `docs/beta/M8_MATCHING_POSTING.md`
- `docs/beta/M8_PERSISTED_SAR_PROJECTION.md`
- `docs/beta/M9_ANALYSIS_AND_AUXILIARY_FACTS.md`
- `docs/beta/M9_CONFIGURED_PERIODS_AND_COMMITMENTS.md`
- `docs/beta/M9_DASHBOARD_CONTROLS.md`
- `docs/beta/M9_DASHBOARD_PRODUCER.md`
- `docs/beta/M9_DASHBOARD_UI.md`
- `docs/beta/M9_DATABASE_READER.md`
- `docs/beta/M9_PERSISTED_FACTS.md`
- `docs/ui-parity/M2_UI_1.json`
- `docs/ui-parity/M2_UI_1.md`
- `docs/ui-parity/M2_UI_2_3.json`
- `docs/ui-parity/M2_UI_2_3.md`
- `scripts/beta-apk-upgrade.sh`
- `scripts/beta-process-death.sh`
- `scripts/check-beta-upgrade.py`

## نتائج التحقق المحلي

التحقق بتاريخ 2026-10-22 على محاكي مستقل Android API 36 ببيانات اصطناعية:

- نجح 2356 اختبار وحدة: التطبيق 2265، والثيم المشترك 13، والمحرك 55، والعقود 2، والتعلم/التحليل 21.
- نجح بناء APK التطبيق واختباراته، و`lintDebug` و`detekt`.
- نجحت اختبارات الميزات المنقولة الـ18 معًا، ومنها الإعداد الكامل والواجهات العربية/الإنجليزية والفاتحة/الداكنة والتعلم والنسخ والاستعادة.
- نجح 13 اختبار جهاز من الفعلي للحفظ وإعادة التشغيل والاستعادة وبوابة الصيانة.
- نجحت رحلات M13 التسع للإيقاف والاستئناف، بإجمالي 18 مرحلة، ومنها الإدخال الحي والاستيراد التاريخي والقرارات اليدوية.
- نجح تحديث APK فوق النسخة السابقة دون مسح بين مرحلتي الاختبار؛ تطابقت الحركات والتفسيرات المخزنة والملكية والتصحيحات والإعدادات.
- نجحت إعادة إنتاج النموذج واختبارات أدواته واختبارات توجيه CI.
- مخطط قاعدة المال v18 مطابق للمخطط السابق؛ فحص APK لا يحتوي أصناف المحرك السابق أو `lab.replica` أو `lab.beta`.
- حُذفت نسخة بناء المقارنة القديمة بعد الاختبار. تقارير التحقق المحلية في `app/build/reports/production-cutover/`.

اختبارات CI الكاملة للجهاز تبقى مطلوبة قبل دمج التغيير أو إصدار نسخة.

## توافق اختبارات CI بعد النقل

- `app/src/main/kotlin/com/baraa/masroof/presentation/analytics/AnalyticsReadViews.kt`: يمكن عرض مركز التنبيهات منفردًا بصفوف الفعلي؛ تظهر صفوف التحليل وإجراءاته عندما يزوّده جذر التطبيق بسير عمل التحليل.
- `app/src/androidTest/kotlin/com/baraa/masroof/instrumentation/JourneySeeder.kt`: تُلتقط رسالة التصحيح وتُفسر عبر مسار الإنتاج، مع التحقق من مبلغها وتاجرها وبقائها في المراجعة قبل التأكيد اليدوي. لم تعد تُزرع حقائق مالية لا يثبتها نص الرسالة، فتظل أدلة الاختبار متسقة عند تحديث القواعد.
