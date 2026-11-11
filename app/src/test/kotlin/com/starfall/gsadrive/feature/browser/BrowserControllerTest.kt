package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.shared.model.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserControllerTest {
    @get:Rule val directory = TemporaryFolder()
    private val first = AccountEntry(AccountType.GOOGLE, "a@example.com", "A")
    private val second = AccountEntry(AccountType.GOOGLE, "b@example.com", "B")
    private var active: AccountEntry? = first

    private fun controller() = BrowserController(
        scope = CoroutineScope(Job()), listingCache = FileListCache(directory.newFolder()),
        activeAccount = { active }, s3Account = { null }, serviceAccount = { null },
        storedAccountName = { _, email -> email }, refreshAccountName = {},
        requestAuthorization = { fail("Unexpected authorization") }, refreshPhotos = {},
        onTokenAvailable = {}, onTokenError = {}
    )

    @Test fun lateListingCannotReplaceAnotherAccountsFiles() {
        val browser = controller()
        val request = browser.newBrowserRefreshTarget(first)
        active = second
        val expected = Model(user = second.name, files = listOf(file("b")))
        browser.model = expected
        assertFalse(browser.updateBrowserTab(request) { it.copy(files = listOf(file("a"))) })
        assertEquals(expected, browser.model)
        assertTrue(browser.browserTabModels.isEmpty())
    }

    @Test fun newFolderRequestRejectsAnOlderListingForTheSameTab() {
        val browser = controller()
        val previous = browser.newBrowserRefreshTarget(first)
        val folder = file("folder", true)
        browser.model = Model(path = listOf(folder))
        val current = browser.newBrowserRefreshTarget(first)
        assertFalse(browser.updateBrowserTab(previous) { it.copy(files = listOf(file("old"))) })
        assertTrue(browser.updateBrowserTab(current) { it.copy(files = listOf(file("child"))) })
        assertEquals(listOf(folder), browser.model.path)
        assertEquals("child", browser.model.files.single().id)
    }

    @Test fun pathChangeRejectsAListingEvenBeforeAnotherRequestStarts() {
        val browser = controller()
        val request = browser.newBrowserRefreshTarget(first)
        browser.model = Model(path = listOf(file("folder", true)))
        assertFalse(browser.updateBrowserTab(request) { it.copy(files = listOf(file("old"))) })
        assertTrue(browser.model.files.isEmpty())
    }

    @Test fun lateAuthorizationCannotUpdateTheNewAccountOrStartLoading() {
        val browser = controller()
        val request = browser.newBrowserRefreshTarget(first)
        active = second
        val expected = Model(user = second.name)
        browser.model = expected
        browser.loadDrive("old-token", request)
        assertEquals(expected, browser.model)
    }

    @Test fun completionForFilesIsRoutedToItsSnapshotWhileSharedIsVisible() {
        val browser = controller()
        browser.model = Model(token = "token", loading = true)
        val request = browser.newBrowserRefreshTarget(first)
        val shared = Model(token = "token", files = listOf(file("shared")))
        browser.browserTabModels[1] = shared
        browser.selectTab(1)
        assertTrue(browser.updateBrowserTab(request) { it.copy(files = listOf(file("own")), loading = false) })
        assertEquals(shared, browser.model)
        browser.selectTab(0)
        assertEquals("own", browser.model.files.single().id)
        assertFalse(browser.model.loading)
    }

    @Test fun accountReactivationCannotAcceptARequestFromBeforeReset() {
        val browser = controller()
        val old = browser.newBrowserRefreshTarget(first)
        browser.clearSnapshots()
        val current = browser.newBrowserRefreshTarget(first)
        assertFalse(browser.isBrowserRefreshCurrent(old))
        assertTrue(browser.isBrowserRefreshCurrent(current))
    }

    @Test fun refreshedTokenReachesBothTabSnapshotsWithoutChangingTheirPaths() {
        val browser = controller()
        val files = Model(token = "old", path = listOf(file("own-folder", true)))
        val shared = Model(token = "old", path = listOf(file("shared-folder", true)))
        browser.model = files
        browser.browserTabModels[0] = files
        browser.browserTabModels[1] = shared
        browser.propagateBrowserToken("fresh")
        assertEquals(files.copy(token = "fresh"), browser.model)
        assertEquals(files.copy(token = "fresh"), browser.browserTabModels[0])
        assertEquals(shared.copy(token = "fresh"), browser.browserTabModels[1])
    }

    @Test fun unsupportedSharedTabCannotReplaceAnS3Listing() {
        val browser = controller()
        active = AccountEntry(AccountType.S3, "bucket", "S3")
        val expected = Model(files = listOf(file("object")))
        browser.model = expected
        browser.selectTab(1)
        assertEquals(0, browser.tab)
        assertEquals(expected, browser.model)
    }

    private fun file(id: String, folder: Boolean = false) = DriveFile(
        id, id, if (folder) "application/vnd.google-apps.folder" else "text/plain", null
    )
}
