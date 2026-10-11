package com.baraa.masroof.presentation.dashboard

import android.content.Context
import android.content.Intent
import com.baraa.masroof.application.locale.AppDigits

object SharePlainText {
    fun share(
        context: Context,
        text: String,
        chooserTitle: String,
        subject: String? = null,
    ) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, AppDigits.display(text))
            if (!subject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, AppDigits.display(subject))
            }
        }
        context.startActivity(Intent.createChooser(send, AppDigits.display(chooserTitle)))
    }
}
