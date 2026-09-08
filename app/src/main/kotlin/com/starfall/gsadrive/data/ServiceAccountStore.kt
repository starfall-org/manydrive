package com.starfall.gsadrive.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ServiceAccountStore(context: Context) {
    private val file = EncryptedJsonFile(context, "service-accounts.enc", "manydrive.service.accounts")
    fun load(): List<ServiceAccountCredentials> {
        val values = file.load()?.getJSONArray("accounts") ?: return emptyList()
        return List(values.length()) { ServiceAccountCredentials.parse(values.getJSONObject(it).toString()) }
    }
    fun save(accounts: List<ServiceAccountCredentials>) {
        val values = JSONArray()
        accounts.forEach { values.put(it.toJson()) }
        file.save(JSONObject().put("accounts", values))
    }
}
