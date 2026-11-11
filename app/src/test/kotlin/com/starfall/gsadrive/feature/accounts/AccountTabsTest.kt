package com.starfall.gsadrive.feature.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTabsTest {
    @Test fun providerTabsRespectExistingCapabilities() {
        val expected = mapOf(
            null to setOf(0),
            AccountType.GOOGLE to setOf(0, 1, 3, 4),
            AccountType.SERVICE to setOf(0, 1, 3),
            AccountType.S3 to setOf(0)
        )
        expected.forEach { (account, tabs) ->
            for (index in -1..5) {
                assertEquals("$account tab $index", index in tabs, isTabEnabled(account, index))
            }
        }
    }
}
