package com.baraa.masroof.lab.beta.learning

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.*
import com.baraa.masroof.lab.bank.learning.*
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.replica.data.learning.FamilyModelCodec
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class FamilyLearningTest {
    private val seed = FamilyModelCodec.seed(File("src/replica/assets/ml/family-seed.json").readText())
    private val bank = BankId("BANK_ALJAZIRA")
    private fun input(body: String) = MessageInput("learning", "AlJazira",body,Instant.parse("2026-10-06T09:00:00Z"),true)
    private fun parse(body: String,classifier: FamilyClassifier?=null) = RuleInterpreter(AlJaziraRuleProfile.current(),classifier?.let {c->FamilyAssist {message,title->c.suggest(bank.value,message.body,title)}}).interpretRouted(input(body),bank)
    @Test fun trainedModelActuallyAssistsAnUnseenTitleWithoutChangingExtractedMoney() {
        val body="حوالة محلية واردة\nبنك المرسل: بنك الرياض\nاودعت الى حساب: 3001\nمن: TEST_EMPLOYER\nمبلغ العملية: SAR 2000.00\nفي: 2026-10-06 09:00"
        assertEquals(MessageFamily.UNKNOWN,parse(body).facts.family)
        val classifier=FamilyClassifier(seed.model)
        val predicted=classifier.model.predict(FamilyFeatures.extract(body).terms)
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
    @Test fun oneExplicitHumanFamilyCorrectionTeachesTheTemplateAndSurvivesSerialization() {
        val body="سداد تمويل خاص\nمن: 3001\nالقسط: SAR 20\nالمبلغ المتبقي: SAR 300\nلـ: تمويل شخصي\nفي: 2026-10-06 09:00"
        val before=parse(body)
        assertEquals(MessageFamily.UNKNOWN,before.facts.family)
        assertEquals(LoanType.PERSONAL,before.facts.loanType)
        val f=FamilyFeatures.extract(body)
        val label=HumanFamilyLabel(bank.value,"raw-one","human-one",MessageFamily.FINANCING_INSTALLMENT,f.template,f.terms,1L,"USER_EVENT_KIND")
        val classifier=FamilyModelCodec.checked(seed,listOf(label))
        val serialized=FamilyModelCodec.cache(seed,listOf(label),classifier)
        val reopened=FamilyModelCodec.checked(seed,FamilyModelCodec.cachedLabels(serialized,seed))
        val after=parse(body.replace("SAR 20","SAR 21"),reopened)
        assertEquals(after.toString(),ParseStatus.SUCCESS,after.status)
        assertEquals(MessageFamily.FINANCING_INSTALLMENT,after.facts.family)
        assertEquals(FamilyOrigin.HUMAN_TEMPLATE,after.familyInference!!.origin)
        assertEquals("21.00",after.facts.amount!!.amount.toPlainString())
        val conflict=label.copy(rawId="raw-two",decisionId="human-two",family=MessageFamily.FEE)
        assertNull(FamilyClassifier(seed.model,listOf(label,conflict)).suggest(bank.value,body,f.title))
    }
    @Test fun corruptedModelsAreRejectedAndCannotLeakAcrossBanks() {
        assertThrows(IllegalArgumentException::class.java) {seed.model.copy(documents=seed.model.documents.mapValues {-1L})}
        val c=FamilyClassifier(seed.model)
        assertNull(c.suggest("OTHER_BANK","حوالة محلية واردة", "حوالة محلية واردة"))
        val encoded=FamilyModelCodec.cache(seed,emptyList(),c)
        assertThrows(IllegalArgumentException::class.java) {FamilyModelCodec.cachedLabels(encoded.replaceFirst("\"checksum\":\"", "\"checksum\":\"0"),seed)}
    }
}
