package com.baraa.masroof.application.backup

import android.content.Context
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec

/** Backup export runs under an access lease; restore/recovery already own exclusive access. */
object MessagePatternBackup {
    fun capture(context: Context): String? = captureOriginal(context).also(::validate)
    fun captureOriginal(context: Context): String? = MessagePatternStore(context).captureRaw()
    fun validate(raw: String?) { if (raw != null) MessagePatternConfigurationCodec.decode(raw) }
    fun restore(context: Context, raw: String?, validate: Boolean = true) = MessagePatternStore(context).restoreRaw(raw, validate)
}
