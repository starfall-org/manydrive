package com.starfall.gsadrive.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.io.encoding.Base64

class ServiceAccountCredentialsTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun json() = JSONObject()
        .put("type", "service_account")
        .put("client_email", "drive@example.iam.gserviceaccount.com")
        .put("private_key_id", "example-key-id")
        .put("token_uri", ServiceAccountCredentials.TOKEN_URI)
        .put("private_key", "-----BEGIN PRIVATE KEY-----\n${Base64.Default.encode(pair.private.encoded)}\n-----END PRIVATE KEY-----\n")

    @Test fun officialCredentialsKeepIdentityKeyAndDriveScope() {
        val account = ServiceAccountCredentials.parse(json().toString())
        val sdk = account.sdkCredentials()
        assertEquals(account.email, sdk.clientEmail)
        assertEquals("example-key-id", sdk.privateKeyId)
        assertArrayEquals(pair.private.encoded, sdk.privateKey.encoded)
        assertEquals(setOf("https://www.googleapis.com/auth/drive"), sdk.scopes.toSet())
        assertEquals(ServiceAccountCredentials.TOKEN_URI, sdk.tokenServerUri.toString())
        assertNull(sdk.serviceAccountUser)
        val data = "test payload".toByteArray()
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(pair.public)
            update(data)
        }
        assertTrue(verifier.verify(sdk.sign(data)))
    }

    @Test fun rejectsOtherCredentialTypesAndNonGoogleTokenEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceAccountCredentials.parse(json().put("type", "authorized_user").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceAccountCredentials.parse(json().put("token_uri", "https://example.com/token").toString())
        }
    }

    @Test fun rejectsMalformedMissingAndOversizedKeysWithoutEchoingSecrets() {
        listOf("{}", "not json", json().put("private_key", "PRIVATE-SECRET").toString(),
            " ".repeat(ServiceAccountCredentials.MAX_JSON_BYTES + 1)).forEach { text ->
            val error = assertThrows(IllegalArgumentException::class.java) { ServiceAccountCredentials.parse(text) }
            assertFalse(error.message.orEmpty().contains("PRIVATE-SECRET"))
        }
    }

    @Test fun normalizedStorageRoundTripsAndDiagnosticsHidePrivateKey() {
        val account = ServiceAccountCredentials.parse(json().put("unexpected", "not retained").toString())
        assertFalse(account.toJson().has("unexpected"))
        val restored = ServiceAccountCredentials.parse(account.toJson().toString())
        assertEquals(account.toJson().toString(), restored.toJson().toString())
        assertFalse(account.toString().contains("PRIVATE KEY"))
        assertFalse(account.toString().contains(Base64.Default.encode(pair.private.encoded)))
    }

    @Test fun refreshesTokenBeforeExpiry() {
        val token = ServiceAccessToken("test-token", 1000)
        assertTrue(token.validAt(939))
        assertFalse(token.validAt(940))
        assertFalse(token.validAt(1001))
    }
}
