package com.baraa.masroof.lab.beta.application.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.evidence.*
import java.time.Instant

/** Only durable capture occurs inside goAsync; interpretation is scheduled by raw id. */
class BetaLiveSmsReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(context.packageName!=BetaLiveSms.HOST_PACKAGE || intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts=Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList().orEmpty()
        if(parts.isEmpty()) return
        val senders=parts.map { it.displayOriginatingAddress }.distinct()
        val sender=senders.singleOrNull()?.takeIf { it.isNotBlank() } ?: return
        val body=parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if(body.isBlank()) return
        val now=System.currentTimeMillis()
        val timestamp=parts.map { it.timestampMillis }.takeIf { values -> values.all { it>0 && it<=now+300_000 } }?.minOrNull()
        val at=Instant.ofEpochMilli(timestamp ?: now)
        val providerId=java.security.MessageDigest.getInstance("SHA-256").digest((sender.length.toString()+":"+sender+body.length+":"+body+":"+at).toByteArray()).joinToString("") { "%02x".format(it) }
        val message=EvidenceMessage(providerId,sender,body,at,if(timestamp!=null) ReceiptTimeSource.SERVICE_CENTER_TIMESTAMP else ReceiptTimeSource.DEVICE_FALLBACK)
        val pending=goAsync()
        BetaIo.execute {
            try { BetaContainer(context.applicationContext).use { beta ->
                val id=beta.captureLive(message) ?: return@use
                val generation=beta.storageGeneration() ?: return@use
                BetaLiveSms.schedule(context.applicationContext,id,generation)
            } } catch (_: Exception) { /* Captured retry rows survive a scheduler failure. No SMS text is logged. */ }
            finally { pending.finish() }
        }
    }
}
