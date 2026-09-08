package com.starfall.gsadrive.data

import org.json.JSONObject
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64

class ServiceAccountCredentials private constructor(
    val email: String,
    private val privateKeyPem: String,
    private val privateKey: RSAPrivateKey,
    private val keyId: String
) {
    val id: String get() = email

    fun toJson(): JSONObject = JSONObject().put("type", "service_account")
        .put("client_email", email).put("private_key", privateKeyPem)
        .put("private_key_id", keyId).put("token_uri", TOKEN_URI)

    internal fun sdkCredentials(): com.google.auth.oauth2.ServiceAccountCredentials =
        com.google.auth.oauth2.ServiceAccountCredentials.newBuilder()
            .setClientEmail(email)
            .setPrivateKey(privateKey)
            .apply { if (keyId.isNotBlank()) setPrivateKeyId(keyId) }
            .setTokenServerUri(java.net.URI(TOKEN_URI))
            .setScopes(listOf("https://www.googleapis.com/auth/drive"))
            .build()

    override fun toString(): String = "ServiceAccountCredentials($email)"

    companion object {
        const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val MAX_JSON_BYTES = 128 * 1024

        fun parse(text: String): ServiceAccountCredentials {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "File JSON quá lớn." }
            val json = try { JSONObject(text) } catch (_: Exception) {
                throw IllegalArgumentException("File không phải JSON hợp lệ.")
            }
            require(json.optString("type") == "service_account") { "Cần file khóa JSON loại service_account." }
            val email = json.optString("client_email").trim()
            require(email.matches(Regex("[^\\s@]+@[^\\s@]+\\.gserviceaccount\\.com"))) { "JSON thiếu client_email hợp lệ." }
            require(json.optString("token_uri", TOKEN_URI) == TOKEN_URI) { "token_uri không phải máy chủ OAuth của Google." }
            val pem = json.optString("private_key").trim()
            require(pem.startsWith("-----BEGIN PRIVATE KEY-----") && pem.endsWith("-----END PRIVATE KEY-----")) {
                "JSON thiếu private_key định dạng PKCS#8."
            }
            val key = try {
                val encoded = pem.removePrefix("-----BEGIN PRIVATE KEY-----").removeSuffix("-----END PRIVATE KEY-----")
                    .replace(Regex("\\s"), "")
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.Default.decode(encoded))) as RSAPrivateKey
            } catch (_: Exception) { throw IllegalArgumentException("Khóa RSA trong JSON không hợp lệ.") }
            require(key.modulus.bitLength() >= 2048) { "Khóa RSA phải có ít nhất 2048 bit." }
            return ServiceAccountCredentials(email, pem, key, json.optString("private_key_id"))
        }
    }
}

class ServiceAccessToken(val value: String, val expiresAtSeconds: Long) {
    fun validAt(nowSeconds: Long) = nowSeconds < expiresAtSeconds - 60
}

object ServiceAccountApi {
    fun accessToken(credentials: ServiceAccountCredentials): ServiceAccessToken {
        val token = credentials.sdkCredentials().refreshAccessToken()
        val expiration = requireNotNull(token.expirationTime) { "Google không trả về thời hạn token." }
        check(token.tokenValue.isNotBlank()) {
            "Google không trả về access token hợp lệ."
        }
        return ServiceAccessToken(token.tokenValue, expiration.time / 1000)
    }
}
