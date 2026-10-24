package com.baraa.masroof.lab.beta.learning

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.*
import com.baraa.masroof.lab.ml.family.*
import com.baraa.masroof.application.interpretation.LabFamilyAssist
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.data.learning.FamilyModelCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FamilyLearningTest {
    private val seed = FamilyModelCodec.seed(requireNotNull(FamilyModel::class.java.getResourceAsStream("/ml/family-seed.json")).bufferedReader().use {it.readText()})
    private val bank = BankId("BANK_ALJAZIRA")
    private fun input(body: String) = MessageInput("learning", "AlJazira",body,Instant.parse("2026-10-06T09:00:00Z"),true)
    private fun parse(body: String,classifier: FamilyClassifier?=null) = RuleInterpreter(AlJaziraRuleProfile.current(),classifier?.let {c->FamilyAssist {message,title->LabFamilyAssist.suggest(c,bank.value,message.body,title)}}).interpretRouted(input(body),bank)
    @Test fun runtimeAndTrainingBridgeShareArabicNormalizationAndExcludePersonalValues() {
        val first="حَوَالَة مَحَلِّيَّة وَارِدَة\nبنك المرسل: بنك الرياض\nاودعت الى حساب: ٣٠٠١\nمن: شخص أول\nمبلغ العملية: SAR ٢٠٠٠.٠٠\nفي: 2026-10-06 09:00"
        val second="حوالة محلية واردة\nبنك المرسل: بنك آخر\nاودعت الى حساب: 9999\nمن: Different Person\nمبلغ العملية: SAR 15.00\nفي: 2026-10-07 12:00"
        val a=requireNotNull(LabFamilyAssist.clean(first).features)
        val b=requireNotNull(LabFamilyAssist.clean(second).features)
        assertEquals(a.terms,b.terms)
        assertEquals(a.template,b.template)
        assertEquals("حوالة محلية واردة",a.title)
        MessageFamily.entries.forEach {assertEquals(it,LabFamilyAssist.toBank(LabFamilyAssist.toModel(it)))}
    }

    @Test fun incompatibleCleaningMetadataCannotLoadASeed() {
        val text=requireNotNull(FamilyModel::class.java.getResourceAsStream("/ml/family-seed.json")).bufferedReader().use {it.readText()}
        assertThrows(IllegalArgumentException::class.java) {FamilyModelCodec.seed(text.replace(FamilyInputCleaner.VERSION,"obsolete-cleaner"))}
        assertThrows(IllegalArgumentException::class.java) {FamilyModelCodec.seed(text.replace(FamilyInputCleaner.FEATURES_VERSION,"obsolete-features"))}
    }
    @Test fun trainedModelActuallyAssistsAnUnseenTitleWithoutChangingExtractedMoney() {
        val body="حوالة محلية واردة\nبنك المرسل: بنك الرياض\nاودعت الى حساب: 3001\nمن: TEST_EMPLOYER\nمبلغ العملية: SAR 2000.00\nفي: 2026-10-06 09:00"
        assertEquals(MessageFamily.UNKNOWN,parse(body).facts.family)
        val classifier=FamilyClassifier(seed.model.copy(enabledFamilies=setOf(SmsFamilyLabel.TRANSFER_IN)))
        val predicted=classifier.model.predict(requireNotNull(LabFamilyAssist.clean(body).features))
        val actual=parse(body,classifier)
        assertEquals("prediction=$predicted result=$actual",ParseStatus.SUCCESS,actual.status)
        assertEquals(MessageFamily.TRANSFER_IN,actual.facts.family)
        assertEquals("2000.00",actual.facts.amount!!.amount.toPlainString())
        assertEquals(FamilyOrigin.ML,actual.familyInference!!.origin)
        assertEquals(seed.model.version,actual.familyInference!!.modelVersion)
        assertNotEquals(ParseStatus.SUCCESS,parse(body.replace("بنك المرسل: بنك الرياض\n",""),classifier).status)
    }
    @Test fun knownRulesNeverAskTheClassifierAndSecurityStillWins() {
        var called=0
        val engine=RuleInterpreter(AlJaziraRuleProfile.current(),FamilyAssist {_,_->called++;FamilySuggestion(MessageFamily.REFUND,1.0,"fake",FamilyOrigin.ML)})
        val body="شراء عبر الانترنت\nبطاقة ائتمانية: 7271\nمبلغ: SAR 60.99\nلدى: TEST\nفي: 2026-10-06 09:00"
        assertEquals(MessageFamily.PURCHASE,engine.interpretRouted(input(body),bank).facts.family)
        assertEquals(0,called)
        assertEquals(ParseStatus.NON_FINANCIAL,engine.interpretRouted(input("رمز التحقق 123456\n$body"),bank).status)
        assertEquals(0,called)
        val blocked="طلب حوالة محلية واردة\nبنك المرسل: بنك الرياض\nاودعت الى حساب: 3001\nمن: TEST_EMPLOYER\nمبلغ العملية: SAR 2000\nفي: 2026-10-06 09:00"
        assertNotEquals(ParseStatus.SUCCESS,parse(blocked,FamilyClassifier(seed.model)).status)
    }
    @Test fun oneExplicitHumanFamilyCorrectionTeachesTheTemplate() {
        val body="سداد تمويل خاص\nمن: 3001\nالقسط: SAR 20\nالمبلغ المتبقي: SAR 300\nلـ: تمويل شخصي\nفي: 2026-10-06 09:00"
        val before=parse(body)
        assertEquals(MessageFamily.UNKNOWN,before.facts.family)
        assertEquals(LoanType.PERSONAL,before.facts.loanType)
        val f=requireNotNull(LabFamilyAssist.clean(body).features)
        val label=HumanFamilyLabel(bank.value,"raw-one","human-one",SmsFamilyLabel.FINANCING_INSTALLMENT,f.template,f.terms,1L,"USER_EVENT_KIND")
        val classifier=FamilyModelCodec.checked(seed,listOf(label))
        val after=parse(body.replace("SAR 20","SAR 21"),classifier)
        assertEquals(after.toString(),ParseStatus.SUCCESS,after.status)
        assertEquals(MessageFamily.FINANCING_INSTALLMENT,after.facts.family)
        assertEquals(FamilyOrigin.HUMAN_TEMPLATE,after.familyInference!!.origin)
        assertEquals("21.00",after.facts.amount!!.amount.toPlainString())
        val conflict=label.copy(rawId="raw-two",decisionId="human-two",family=SmsFamilyLabel.FEE)
        assertNull(FamilyClassifier(seed.model,listOf(label,conflict)).suggest(bank.value,f))
    }
    @Test fun personalTemplateKeepsItsSourceWhenZeroPrincipalFailsFinancialValidation() {
        val body="سداد تمويل خاص\nمن: 3001\nالقسط: SAR 20\nالمبلغ المتبقي: SAR 300\nلـ: تمويل شخصي\nفي: 2026-10-06 09:00"
        val features=requireNotNull(LabFamilyAssist.clean(body).features)
        val label=HumanFamilyLabel(bank.value,"raw-one","human-one",SmsFamilyLabel.FINANCING_INSTALLMENT,features.template,features.terms,1L,"USER_EVENT_KIND")
        val classifier=FamilyPersonalization.rebuild(seed.model,listOf(label),seed.evaluation).classifier
        val invalid=body.replace("SAR 20","SAR 0.00")
        val result=com.baraa.masroof.bank.InterpretationDraftMapper().map(input(invalid),parse(invalid,classifier),com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA)
        assertTrue(result.toString(), result is com.baraa.masroof.parsing.model.ParseResult.ReviewRequired)
        val event=requireNotNull((result as com.baraa.masroof.parsing.model.ParseResult.ReviewRequired).event)
        val provenance=com.baraa.masroof.application.interpretation.InterpretationProvenanceWorkflow.project(com.baraa.masroof.parsing.repository.ParsedEventRecord(event,result.details))
        assertEquals(com.baraa.masroof.application.interpretation.InterpretationOrigin.HUMAN_TEMPLATE,provenance.origin)
        assertTrue(provenance.pendingReview)
    }

    @Test fun aLaterHumanIgnoreRevokesTheFinancialTemplateWithoutAutoIgnoringNewMessages() {
        val body="سداد تمويل خاص\nمن: 3001\nالقسط: SAR 20\nالمبلغ المتبقي: SAR 300\nلـ: تمويل شخصي\nفي: 2026-10-06 09:00"
        val f=requireNotNull(LabFamilyAssist.clean(body).features)
        val earlier=HumanFamilyLabel(bank.value,"raw","loan",SmsFamilyLabel.FINANCING_INSTALLMENT,f.template,f.terms,1L,"USER_EVENT_KIND")
        val ignored=earlier.copy(decisionId="ignored",family=SmsFamilyLabel.NON_FINANCIAL,at=2L,provenance="USER_NON_FINANCIAL")
        val actual=parse(body,FamilyModelCodec.checked(seed,listOf(earlier,ignored)))
        assertEquals(MessageFamily.UNKNOWN,actual.facts.family)
        assertNotEquals(ParseStatus.SUCCESS,actual.status)
    }
    @Test fun corruptedModelsAreRejectedAndCannotLeakAcrossBanks() {
        assertThrows(IllegalArgumentException::class.java) {seed.model.copy(documents=seed.model.documents.mapValues {-1L})}
        val c=FamilyClassifier(seed.model)
        assertNull(c.suggest("OTHER_BANK",requireNotNull(LabFamilyAssist.clean("حوالة محلية واردة").features)))
    }
    @Test fun rebuildingFromHumanDecisionsKeepsTemplateBlocks() {
        val features=requireNotNull(LabFamilyAssist.clean("سداد تمويل خاص\nمن: 3001\nالقسط: SAR 20").features)
        val label=HumanFamilyLabel(bank.value,"raw-one","label",SmsFamilyLabel.FINANCING_INSTALLMENT,features.template,features.terms,1,"USER_MESSAGE_FAMILY")
        val block=HumanTemplateBlock(bank.value,"raw-two","edit",features.template,2)
        val classifier=FamilyModelCodec.checked(seed,listOf(label),listOf(block))
        val reopened=FamilyModelCodec.checked(seed,classifier.latestLabels,classifier.latestBlocks)
        assertEquals(classifier.version,reopened.version)
        assertEquals(listOf(block),reopened.latestBlocks)
        assertNull(reopened.suggest(bank.value,features))
    }
}
