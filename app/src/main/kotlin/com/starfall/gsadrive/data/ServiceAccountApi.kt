package com.starfall.gsadrive.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
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

    fun assertion(nowSeconds: Long): String {
        fun encode(value: ByteArray) = Base64.UrlSafe.encode(value).trimEnd('=')
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").apply {
            if (keyId.isNotBlank()) put("kid", keyId)
        }
        val claims = JSONObject().put("iss", email).put("scope", "https://www.googleapis.com/auth/drive")
            .put("aud", TOKEN_URI).put("iat", nowSeconds).put("exp", nowSeconds + 3600)
        val unsigned = encode(header.toString().toByteArray(Charsets.UTF_8)) + "." +
            encode(claims.toString().toByteArray(Charsets.UTF_8))
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(unsigned.toByteArray(Charsets.UTF_8))
        }.sign()
        return "$unsigned.${encode(signature)}"
    }

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
        val now = System.currentTimeMillis() / 1000
        val payload = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
            "&assertion=" + URLEncoder.encode(credentials.assertion(now), "UTF-8")
        val connection = (URI(ServiceAccountCredentials.TOKEN_URI).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) {
                "Google từ chối Service Account (${connection.responseCode}). Kiểm tra khóa và trạng thái tài khoản."
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val token = json.optString("access_token")
            val expiry = json.optLong("expires_in")
            check(token.isNotBlank() && expiry in 1..3600) { "Google không trả về access token hợp lệ." }
            return ServiceAccessToken(token, now + expiry)
        } finally { connection.disconnect() }
    }
}
