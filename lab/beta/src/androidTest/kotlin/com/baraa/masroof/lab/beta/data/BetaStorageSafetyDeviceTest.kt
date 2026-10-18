package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.verification.BetaStorageChecks
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaStorageSafetyDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun missingDatabaseRetainsSidecars() = BetaStorageChecks.missingDatabaseRetainsSidecars(context)
    @Test fun emptyDatabaseRetainsSidecars() = BetaStorageChecks.emptyDatabaseRetainsSidecars(context)
    @Test fun missingAcknowledgedDatabaseRetainsBackup() = BetaStorageChecks.missingAcknowledgedDatabaseRetainsBackup(context)
}
