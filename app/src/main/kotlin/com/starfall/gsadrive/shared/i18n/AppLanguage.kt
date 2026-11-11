package com.starfall.gsadrive.shared.i18n

import android.app.Application
import androidx.annotation.StringRes

/** Application context for resource lookups in non-UI code and background services. */
class ManyDriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ManyDriveApplication
            private set
    }
}

internal var testStringResolver: ((Int, Array<out Any?>) -> String)? = null

fun localized(@StringRes id: Int, vararg args: Any?): String {
    testStringResolver?.let { return it(id, args) }
    return if (args.isEmpty()) ManyDriveApplication.instance.getString(id)
    else ManyDriveApplication.instance.getString(id, *args.map { it.toString() }.toTypedArray())
}
