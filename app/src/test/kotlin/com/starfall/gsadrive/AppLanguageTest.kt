package com.starfall.gsadrive

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun vietnameseIsPreserved() {
        assertEquals("Cài đặt", AppLanguage.translate("Cài đặt", Locale("vi")))
    }

    @Test fun englishIsTheFallbackForEnglishAndUnsupportedLocales() {
        assertEquals("Settings", AppLanguage.translate("Cài đặt", Locale.ENGLISH))
        assertEquals("Settings", AppLanguage.translate("Cài đặt", Locale.JAPANESE))
    }

    @Test fun dynamicMessagesAreTranslated() {
        assertEquals("Sign out of An?", AppLanguage.translate("Đăng xuất khỏi An?", Locale.FRENCH))
    }

    @Test fun untranslatedCopyNeverLeaksVietnameseToFallbackLocales() {
        assertEquals("An unexpected error occurred.", AppLanguage.translate("Chuỗi mới", Locale.GERMAN))
    }
}
