package com.starfall.gsadrive.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class S3Account(val id: String = UUID.randomUUID().toString(), val name: String, val config: S3Config)
data class S3Accounts(val accounts: List<S3Account> = emptyList(), val activeId: String? = null)

/** Credentials are encrypted with a device-bound key and excluded from backup. */
class S3AccountStore(context: Context) {
    private val file = EncryptedJsonFile(context, "s3-accounts.enc", "manydrive.s3.accounts")

    fun load(): S3Accounts {
        val json = file.load() ?: return S3Accounts()
        val items = json.getJSONArray("accounts")
        return S3Accounts(List(items.length()) { index ->
            val item = items.getJSONObject(index)
            S3Account(item.getString("id"), item.getString("name"), S3Config(
                item.getString("endpoint"), item.getString("accessKey"), item.getString("secretKey"),
                item.getString("bucket"), item.getString("region")
            ))
        }, json.optString("activeId").takeIf { it.isNotEmpty() })
    }

    fun save(value: S3Accounts) {
        val items = JSONArray()
        value.accounts.forEach { account ->
            items.put(JSONObject().put("id", account.id).put("name", account.name)
                .put("endpoint", account.config.endpoint).put("accessKey", account.config.accessKey)
                .put("secretKey", account.config.secretKey).put("bucket", account.config.bucket)
                .put("region", account.config.region))
        }
        val json = JSONObject().put("accounts", items).put("activeId", value.activeId.orEmpty())
        file.save(json)
    }
}
