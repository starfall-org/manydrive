package com.starfall.gsadrive.data

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.io.File

/** Shared Google transport; tokens remain scoped to each request/client. */
internal object GoogleApiClient {
    val transport = NetHttpTransport()
    val jsonFactory = GsonFactory.getDefaultInstance()

    fun initializer(accessToken: String, readTimeoutMillis: Int = 120_000): HttpRequestInitializer {
        val credentials = HttpCredentialsAdapter(GoogleCredentials.create(AccessToken(accessToken, null)))
        return HttpRequestInitializer { request ->
            credentials.initialize(request)
            request.connectTimeout = 15_000
            request.readTimeout = readTimeoutMillis
            request.isLoggingEnabled = false
            request.isCurlLoggingEnabled = false
        }
    }

    // Photos REST fallback; see README for service-specific SDK limitations.
    fun request(accessToken: String, method: String, address: String, payload: ByteArray? = null): ByteArray {
        val request = transport.createRequestFactory(initializer(accessToken)).buildRequest(
            method, GenericUrl(address), payload?.let { ByteArrayContent("application/json", it) }
        )
        val response = request.execute()
        return try { response.content?.use { it.readBytes() } ?: ByteArray(0) }
        finally { response.disconnect() }
    }

    fun download(accessToken: String, address: String, target: File) {
        val response = transport.createRequestFactory(initializer(accessToken, 180_000))
            .buildGetRequest(GenericUrl(address)).execute()
        try {
            target.parentFile?.mkdirs()
            target.outputStream().buffered().use { response.download(it) }
        } finally { response.disconnect() }
    }
}
