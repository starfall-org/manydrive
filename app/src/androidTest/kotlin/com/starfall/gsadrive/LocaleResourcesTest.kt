package com.starfall.gsadrive

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleResourcesTest {
    private fun contextFor(tag: String): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    @Test fun androidResolvesSupportedLocalesAndFallback() {
        assertEquals("Settings", contextFor("en").getString(R.string.settings))
        assertEquals("Cài đặt", contextFor("vi").getString(R.string.settings))
        assertEquals("Settings", contextFor("ar").getString(R.string.settings))
        assertNotEquals(contextFor("zh-CN").getString(R.string.files), contextFor("zh-TW").getString(R.string.files))
    }

    @Test fun formatsArgumentsThroughAndroidResources() {
        val result = contextFor("fr").getString(R.string.sign_out_of_arg_1_2, "An")
        assertTrue(result.contains("An"))
    }
}
