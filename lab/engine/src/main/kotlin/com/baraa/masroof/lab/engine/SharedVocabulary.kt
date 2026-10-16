package com.baraa.masroof.lab.engine

/** Closed extraction/observation vocabulary; values remain escaped literal phrases. */
object SharedVocabulary {
    val detailFields = setOf("dueAmount", "foreignAmount", "counterparty", "transactionReference", "biller", "billerCode", "paymentDueDate")
    val observationFields = setOf("purchase.pos", "purchase.online", "card.credit", "card.debit", "card.statement", "card.available_balance", "network.intra", "network.inter", "income.salary", "loan.personal", "loan.auto", "loan.mortgage")
}
