package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaStorageChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class BetaStorageSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun missingDatabaseRetainsSidecars() = BetaStorageChecks.missingDatabaseRetainsSidecars(context)
    @Test fun emptyDatabaseRetainsSidecars() = BetaStorageChecks.emptyDatabaseRetainsSidecars(context)
    @Test fun missingAcknowledgedDatabaseRetainsBackup() = BetaStorageChecks.missingAcknowledgedDatabaseRetainsBackup(context)
}
