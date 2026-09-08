package com.starfall.gsadrive.data

import android.content.Context
import org.json.JSONArray

/** Stores account identifiers only. OAuth and S3 secrets are never written here. */
class AccountStore(context: Context) {
    private val preferences = context.getSharedPreferences("manydrive_accounts", Context.MODE_PRIVATE)

    fun accounts(): List<String> = runCatching {
        val values = JSONArray(preferences.getString("accounts", "[]"))
        List(values.length()) { values.getString(it) }
    }.getOrDefault(emptyList())

    fun active(): String? = preferences.getString("active", null)

    fun clearActive() {
        preferences.edit().remove("active").apply()
    }

    fun select(email: String) {
        val next = (accounts() + email).distinct()
        preferences.edit().putString("accounts", JSONArray(next).toString()).putString("active", email).apply()
    }

    fun remove(email: String) {
        val next = accounts().filterNot { it == email }
        preferences.edit().putString("accounts", JSONArray(next).toString()).apply()
    }
}
