package com.baraa.masroof.application.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLocaleWindowConfigurationTest {
    @Test fun localeOverrideCannotCaptureWindowQualifiers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var capturedOverride: Configuration? = null
        val recording = object : ContextWrapper(context) {
            override fun createConfigurationContext(configuration: Configuration): Context {
                capturedOverride = Configuration(configuration)
                return super.createConfigurationContext(configuration)
            }
        }
        AppLocaleContextFactory.wrap(recording, AppLocale.TAG_AR)
        val delta = requireNotNull(capturedOverride)
        assertEquals("ar", delta.locales[0].language)
        assertEquals(0, delta.screenWidthDp)
        assertEquals(0, delta.screenHeightDp)
        assertEquals(0, delta.smallestScreenWidthDp)
        assertEquals(0, delta.densityDpi)
        assertEquals(0, delta.uiMode)
        assertEquals(0f, delta.fontScale, 0f)
    }
}
