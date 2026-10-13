package com.baraa.masroof.lab.data

import android.content.ContentResolver
import android.provider.Telephony
import com.baraa.masroof.lab.engine.RulePackValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LabInboxMessage(val providerId: String, val sender: String, val body: String, val receivedAt: Long)
data class LabInboxRead(val messages: List<LabInboxMessage>, val skipped: Int = 0)

interface LabInboxSource {
    suspend fun read(senders: Set<String>, fromInclusive: Long, toExclusive: Long): LabInboxRead
}

/** Read-only access. Never calls a production SMS scanner or writes to the SMS provider. */
class AndroidLabInboxSource(private val resolver: ContentResolver) : LabInboxSource {
    override suspend fun read(senders: Set<String>, fromInclusive: Long, toExclusive: Long): LabInboxRead =
        withContext(Dispatchers.IO) {
            require(senders.isNotEmpty() && toExclusive > fromInclusive)
            val allowed = senders.map(RulePackValidation::senderKey).toSet()
            val messages = mutableListOf<LabInboxMessage>()
            var skipped = 0
            val cursor = resolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
                arrayOf(fromInclusive.toString(), toExclusive.toString()),
                "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC")
                ?: error("تعذر قراءة صندوق الرسائل؛ لم تُحفظ رسائل")
            cursor.use { c ->
                val idIndex = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val senderIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    coroutineContext.ensureActive()
                    // Read sender first; unrelated bodies are neither inspected nor persisted.
                    val sender = c.getString(senderIndex) ?: continue
                    if (RulePackValidation.senderKey(sender) !in allowed) continue
                    val date = if (c.isNull(dateIndex)) null else c.getLong(dateIndex)
                    if (date == null || date < fromInclusive || date >= toExclusive) continue
                    val id = c.getString(idIndex)
                    val body = c.getString(bodyIndex)
                    if (id.isNullOrBlank() || body.isNullOrBlank() || body.length > 16_000) {
                        skipped++;continue
                    }
                    require(messages.size < MAX_MESSAGES) { "النطاق يتجاوز 1000 رسالة للبنك؛ اختر فترة أقصر. لم يُستورد شيء" }
                    messages += LabInboxMessage(id, sender, body, date)
                }
            }
            LabInboxRead(messages, skipped)
        }

    companion object { const val MAX_MESSAGES = 1_000 }
}
