package com.starfall.gsadrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.starfall.gsadrive.app.App
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.AccountUi
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.i18n.localized
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrashNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sidebarTrashStaysOpenAndCanReturnToFiles() {
        val selected = mutableIntStateOf(0)
        val account = AccountEntry(AccountType.GOOGLE, "test@example.com", "Test")
        compose.setContent {
            MaterialTheme {
                App(
                    model = Model(), selected = selected.intValue,
                    select = { selected.intValue = it }, reload = {}, signIn = {},
                    authorize = {}, connectS3 = { _, _ -> }, signOut = {}, upload = {},
                    createFolder = {}, trash = {},
                    accounts = AccountUi(entries = listOf(account), active = account)
                )
            }
        }
        compose.onNodeWithContentDescription(localized(R.string.open_menu)).performClick()
        compose.onNodeWithText(localized(R.string.trash)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected.intValue) }
        compose.onNodeWithText(localized(R.string.trash_is_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription(localized(R.string.back)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, selected.intValue) }
    }
}
