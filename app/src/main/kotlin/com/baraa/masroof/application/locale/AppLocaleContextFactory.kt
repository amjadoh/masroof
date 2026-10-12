package com.baraa.masroof.application.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps a [Context] with the user's selected display locale.
 */
object AppLocaleContextFactory {
    fun wrap(context: Context, languageTag: String): Context {
        val locale = AppLocale.displayLocale(languageTag)
        Locale.setDefault(locale)
        // Override locale only. Copying the entire configuration pins window bounds,
        // density and screen qualifiers when a foldable or multi-window host resizes.
        val config = Configuration()
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
