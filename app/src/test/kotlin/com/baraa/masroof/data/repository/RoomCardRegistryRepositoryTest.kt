package com.baraa.masroof.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.data.room.MasroofDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomCardRegistryRepositoryTest {
    @Test fun configuringSupplementaryUsesParentFacilityWithoutChangingParentOrSiblings() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)
        repository.setSupplementaryCard(CardReference(bank, "2222"), "1111")
        seedCard("3333", CardRole.STANDALONE)
        repository.markAsDebit(CardReference(bank, "3333"))
        database.cardRegistryDao().updateLinkedAccount(bank.id, "3333", bank.id, "3001")
        val others = repository.listAll().filter { it.last4 != "3333" }
        repository.configure(CardReference(bank, "3333"), com.baraa.masroof.domain.model.CardConfiguration(
            com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.CREDIT, CardRole.SUPPLEMENTARY, "1111"))
        val corrected = repository.get(CardReference(bank, "3333"))!!
        assertEquals(CardType.CREDIT, corrected.cardType)
        assertEquals(CardRole.SUPPLEMENTARY, corrected.cardRole)
        assertEquals("1111", corrected.parentCardLast4)
        assertEquals(null, corrected.linkedAccount)
        assertEquals(repository.get(CardReference(bank, "1111"))?.creditFacilityId, corrected.creditFacilityId)
        assertEquals(others, repository.listAll().filter { it.last4 != "3333" })
    }

    @Test fun networkCorrectionPreservesExistingFacilityAndAllSupplementaries() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)
        repository.setSupplementaryCard(CardReference(bank, "2222"), "1111")
        database.cardRegistryDao().insertFacilityIfAbsent("preserved-facility", bank.id, "1111")
        database.cardRegistryDao().updateCreditFacilityId(bank.id, "1111", "preserved-facility")
        database.cardRegistryDao().updateCreditFacilityId(bank.id, "2222", "preserved-facility")
        val child = repository.get(CardReference(bank, "2222"))
        repository.configure(CardReference(bank, "1111"), com.baraa.masroof.domain.model.CardConfiguration(
            com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.CREDIT, CardRole.PRIMARY))
        assertEquals("preserved-facility", repository.get(CardReference(bank, "1111"))?.creditFacilityId)
        assertEquals(child, repository.get(CardReference(bank, "2222")))
    }

    @Test fun setupWriteFailureRollsBackNetworkTypeAndFacility() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.STANDALONE)
        val before = repository.listAll()
        fun facilityCount() = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM credit_facility").use {
            it.moveToFirst(); it.getInt(0)
        }
        val facilities = facilityCount()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_setup BEFORE UPDATE OF cardType ON card_registry BEGIN SELECT RAISE(ABORT, 'test-write-failure'); END")
        assertThrows(Exception::class.java) { runBlocking {
            repository.configure(CardReference(bank, "1111"), com.baraa.masroof.domain.model.CardConfiguration(
                com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.CREDIT, CardRole.PRIMARY))
        } }
        assertEquals(before, repository.listAll())
        assertEquals(facilities, facilityCount())
    }

    @Test fun cardTypeSuggestionUsesSuccessfulBankScopedFactsWithoutChangingRegistry() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        val card = CardReference(bank, "1111")
        seedCard("1111", CardRole.STANDALONE)
        val parsed = RoomParsedEventRepository(database.parsedEventDao())
        val accounts = RoomAccountRegistryRepository.from(database)
        val loans = com.baraa.masroof.domain.repository.NoOpLoanRegistryRepository
        val workflow = com.baraa.masroof.application.settings.SettingsRegistryWorkflow(repository, accounts, loans,
            com.baraa.masroof.domain.ownership.OwnershipConfirmationService(accounts, repository, loans), parsed)
        listOf("credit", "foreign", "review", "conflicting").forEach { id ->
            database.rawSmsDao().insertIfAbsent(com.baraa.masroof.data.room.entity.RawSmsEntity(
                id, "TEST", "TEST", 1000, null, "hash-$id", "dedupe-$id"))
        }
        val before = repository.listAll()
        fun event(id: String, reference: CardReference = card, status: com.baraa.masroof.domain.model.ParseStatus = com.baraa.masroof.domain.model.ParseStatus.SUCCESS) =
            com.baraa.masroof.domain.model.ParsedEvent(id, id, reference.bank, com.baraa.masroof.domain.model.MessageFamily.PURCHASE,
                com.baraa.masroof.domain.model.MoneyDirection.OUTGOING, null, null, null, null, reference, null, null, null, null,
                com.baraa.masroof.domain.model.Confidence(1.0), status)
        parsed.save(event("credit"), com.baraa.masroof.parsing.model.ParsedEventDetails(cardSmsChannel = com.baraa.masroof.parsing.model.CardSmsChannel.CREDIT))
        parsed.save(event("foreign", CardReference(Bank("OTHER"), "1111")), com.baraa.masroof.parsing.model.ParsedEventDetails(cardSmsChannel = com.baraa.masroof.parsing.model.CardSmsChannel.DEBIT))
        parsed.save(event("review", status = com.baraa.masroof.domain.model.ParseStatus.REVIEW_REQUIRED), com.baraa.masroof.parsing.model.ParsedEventDetails(cardSmsChannel = com.baraa.masroof.parsing.model.CardSmsChannel.DEBIT))
        assertEquals(CardType.CREDIT, workflow.suggestedCardType(card))
        assertEquals(before, repository.listAll())
        parsed.save(event("conflicting"), com.baraa.masroof.parsing.model.ParsedEventDetails(cardSmsChannel = com.baraa.masroof.parsing.model.CardSmsChannel.DEBIT))
        assertEquals(null, workflow.suggestedCardType(card))
        assertEquals(before, repository.listAll())
    }

    @Test fun correctingLegacyDebitToVisaCreditPreservesOtherCardsAndTransactions() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)
        repository.setSupplementaryCard(CardReference(bank, "2222"), "1111")
        seedCard("3333", CardRole.STANDALONE)
        repository.markAsDebit(CardReference(bank, "3333"))
        database.cardRegistryDao().updateLinkedAccount(bank.id, "3333", bank.id, "3001")
        val others = repository.listAll().filter { it.last4 != "3333" }
        val tx = com.baraa.masroof.data.room.entity.FinancialTransactionEntity("preserved-tx", "EXPENSE", "42.00", "SAR", 1000,
            "original-container", null, "TEST", null, null)
        database.financialTransactionDao().insertTransactionIfAbsent(tx)
        repository.configure(CardReference(bank, "3333"), com.baraa.masroof.domain.model.CardConfiguration(
            com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.CREDIT, CardRole.PRIMARY))
        val corrected = repository.get(CardReference(bank, "3333"))!!
        assertEquals(CardType.CREDIT, corrected.cardType)
        assertEquals(com.baraa.masroof.domain.model.CardNetwork.VISA, corrected.cardNetwork)
        assertEquals(CardRole.PRIMARY, corrected.cardRole)
        assertEquals(null, corrected.linkedAccount)
        assertEquals(others, repository.listAll().filter { it.last4 != "3333" })
        assertEquals(tx, database.financialTransactionDao().getById("preserved-tx"))
    }

    @Test fun configuredVisaDebitKeepsItsNetworkAndClearsItsOwnCreditRole() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("3333", CardRole.PRIMARY)
        val accounts = RoomAccountRegistryRepository.from(database)
        val account = com.baraa.masroof.domain.model.AccountReference(bank, "3001")
        accounts.observe(account, "sms-account")
        accounts.setOwnership(account, OwnershipStatus.OWNED)
        repository.configure(CardReference(bank, "3333"), com.baraa.masroof.domain.model.CardConfiguration(
            com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.DEBIT, linkedAccount = account))
        val card = repository.get(CardReference(bank, "3333"))!!
        assertEquals(CardType.DEBIT, card.cardType)
        assertEquals(com.baraa.masroof.domain.model.CardNetwork.VISA, card.cardNetwork)
        assertEquals(null, card.cardRole)
        assertEquals(null, card.creditFacilityId)
        assertEquals(account, card.linkedAccount)
    }

    @Test fun setupRejectsChangingAParentWithChildrenAndRollsBackAllFields() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)
        repository.setSupplementaryCard(CardReference(bank, "2222"), "1111")
        val before = repository.listAll()
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            repository.configure(CardReference(bank, "1111"), com.baraa.masroof.domain.model.CardConfiguration(
                com.baraa.masroof.domain.model.CardNetwork.MADA, CardType.DEBIT))
        } }
        assertEquals(before, repository.listAll())
    }

    @Test fun setupRejectsUnknownAccountAndInvalidParentWithoutPartialNetworkChange() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        seedCard("1111", CardRole.STANDALONE)
        val before = repository.listAll()
        val invalid = listOf(
            com.baraa.masroof.domain.model.CardConfiguration(com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.DEBIT,
                linkedAccount = com.baraa.masroof.domain.model.AccountReference(bank, "missing")),
            com.baraa.masroof.domain.model.CardConfiguration(com.baraa.masroof.domain.model.CardNetwork.VISA, CardType.CREDIT,
                CardRole.SUPPLEMENTARY, "missing"),
            com.baraa.masroof.domain.model.CardConfiguration(com.baraa.masroof.domain.model.CardNetwork.MADA, CardType.CREDIT, CardRole.PRIMARY),
        )
        invalid.forEach { draft ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.configure(CardReference(bank, "1111"), draft) } }
            assertEquals(before, repository.listAll())
        }
    }

    @Test fun correctingOneBankDoesNotTouchSameSuffixAtAnotherBankOrObservationIdentity() = runBlocking {
        val bank = Bank.BANK_ALJAZIRA
        val other = Bank("OTHER")
        seedCard("1111", CardRole.STANDALONE)
        repository.observe(CardReference(other, "1111"), "other-sms")
        val old = repository.get(CardReference(bank, "1111"))!!
        val foreign = repository.get(CardReference(other, "1111"))!!
        repository.configure(CardReference(bank, "1111"), com.baraa.masroof.domain.model.CardConfiguration(null, CardType.CREDIT, CardRole.STANDALONE))
        val current = repository.get(CardReference(bank, "1111"))!!
        assertEquals(old.id, current.id)
        assertEquals(old.ownership, current.ownership)
        assertEquals(old.firstSeenRawSmsId, current.firstSeenRawSmsId)
        assertEquals(old.lastSeenRawSmsId, current.lastSeenRawSmsId)
        assertEquals(foreign, repository.get(CardReference(other, "1111")))
    }

    private lateinit var database: MasroofDatabase
    private lateinit var repository: RoomCardRegistryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCardRegistryRepository.from(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun setPrimaryCard_demotesExistingPrimary() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)

        repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"))

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.STANDALONE, cards.getValue("1111").cardRole)
        assertEquals(CardRole.PRIMARY, cards.getValue("2222").cardRole)
    }

    @Test
    fun setSupplementaryCard_rejectsSelfParent() {
        runBlocking {
            seedCard("1111", CardRole.PRIMARY)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "1111"), "1111")
                }
            }
        }
    }

    @Test
    fun setSupplementaryCard_rejectsMissingPrimary() {
        runBlocking {
            seedCard("2222", CardRole.STANDALONE)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "9999")
                }
            }
        }
    }

    @Test
    fun clearCardRole_onPrimary_detachesSupplementaries() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")

        repository.clearCardRole(CardReference(Bank.BANK_ALJAZIRA, "1111"))

        val supplement = repository.get(CardReference(Bank.BANK_ALJAZIRA, "2222"))!!
        assertEquals(CardRole.STANDALONE, supplement.cardRole)
        assertEquals(null, supplement.parentCardLast4)
    }

    @Test
    fun setPrimaryCard_demotesPrimaryAndDetachesSupplementaries() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "3333"), "sms-3333")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "3333"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "3333"), "1111")
        seedCard("4444", CardRole.STANDALONE)

        repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "4444"))

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.STANDALONE, cards.getValue("1111").cardRole)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
        assertEquals(null, cards.getValue("2222").parentCardLast4)
        assertEquals(CardRole.STANDALONE, cards.getValue("3333").cardRole)
        assertEquals(CardRole.PRIMARY, cards.getValue("4444").cardRole)
    }

    @Test
    fun setSupplementaryCard_onPrimaryWithChildren_detachesChildren() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")
        seedCard("3333", CardRole.PRIMARY)

        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "1111"), "3333")

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.SUPPLEMENTARY, cards.getValue("1111").cardRole)
        assertEquals("3333", cards.getValue("1111").parentCardLast4)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
        assertEquals(null, cards.getValue("2222").parentCardLast4)
    }

    @Test
    fun linkDebitToAccount_rejectsCrossBank() {
        runBlocking {
            seedCard("1111", CardRole.STANDALONE)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.linkDebitToAccount(
                        CardReference(Bank.BANK_ALJAZIRA, "1111"),
                        com.baraa.masroof.domain.model.AccountReference(
                            Bank("D360"),
                            "3001",
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun linkDebitToAccount_clearsPrimaryRoleAndChildren() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")

        repository.linkDebitToAccount(
            CardReference(Bank.BANK_ALJAZIRA, "1111"),
            com.baraa.masroof.domain.model.AccountReference(Bank.BANK_ALJAZIRA, "3001"),
        )

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardType.DEBIT, cards.getValue("1111").cardType)
        assertEquals(null, cards.getValue("1111").cardRole)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
    }

    @Test
    fun setPrimaryCard_rejectsDebitCard() {
        runBlocking {
            seedCard("1111", CardRole.STANDALONE)
            repository.updateCardType(CardReference(Bank.BANK_ALJAZIRA, "1111"), CardType.DEBIT)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "1111"))
                }
            }
        }
    }

    @Test
    fun markAsDebit_clearsPrimaryRoleAndSetsDebitType() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")

        repository.markAsDebit(CardReference(Bank.BANK_ALJAZIRA, "1111"))

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardType.DEBIT, cards.getValue("1111").cardType)
        assertEquals(null, cards.getValue("1111").cardRole)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
        assertEquals(com.baraa.masroof.domain.model.CardNetwork.MADA, cards.getValue("1111").cardNetwork)
    }

    private suspend fun seedCard(last4: String, role: CardRole) {
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, last4), "sms-$last4")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, last4), OwnershipStatus.OWNED)
        when (role) {
            CardRole.PRIMARY -> repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, last4))
            CardRole.SUPPLEMENTARY -> error("use setSupplementaryCard")
            CardRole.STANDALONE -> repository.updateCardType(
                CardReference(Bank.BANK_ALJAZIRA, last4),
                CardType.CREDIT,
            )
        }
    }
}
