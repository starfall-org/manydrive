package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.data.DriveStorageAccess
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.data.S3StorageAccess
import com.starfall.gsadrive.shared.i18n.clearTestStrings
import com.starfall.gsadrive.shared.i18n.installTestStrings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StorageSessionAccessTest {
    @Before fun strings() = installTestStrings()
    @After fun clearStrings() = clearTestStrings()

    private fun google(id: String, token: String?) = StorageSession(
        AccountEntry(AccountType.GOOGLE, id, id), token, null, null
    )

    @Test fun capturedSessionKeepsItsTokenWhenSelectionChanges() {
        var selected = google("first", "first-token")
        val captured = selected
        selected = google("second", "second-token")
        assertEquals("first-token", captured.requireDriveToken())
        assertEquals("second-token", selected.requireDriveToken())
        assertTrue(captured.openStorage() is DriveStorageAccess)
    }

    @Test fun s3DoesNotRequireDriveCredentials() {
        val session = StorageSession(
            AccountEntry(AccountType.S3, "s3", "S3"), null,
            S3Config("https://example.com", "key", "secret", "bucket"), null
        )
        assertTrue(session.openStorage() is S3StorageAccess)
    }

    @Test(expected = IllegalArgumentException::class)
    fun googleWithoutAuthorizationFailsBeforeAnyProviderRequest() {
        google("first", null).openStorage()
    }

    @Test(expected = IllegalArgumentException::class)
    fun serviceAccountCannotFallBackToAnExistingGoogleToken() {
        StorageSession(AccountEntry(AccountType.SERVICE, "service", "Service"), "google-token", null, null)
            .openStorage()
    }
}
