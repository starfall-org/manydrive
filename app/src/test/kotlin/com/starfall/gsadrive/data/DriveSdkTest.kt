package com.starfall.gsadrive.data

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import org.junit.Assert.*
import org.junit.Test

class DriveSdkTest {
    @Test fun listsAllPagesAndPreservesMetadata() {
        val urls = mutableListOf<String>()
        val responses = ArrayDeque(listOf(
            """{"nextPageToken":"next +/","files":[{"id":"folder","name":"Folder","mimeType":"application/vnd.google-apps.folder","parents":["root"]}]}""",
            """{"files":[{"id":"file","name":"large.bin","mimeType":"application/octet-stream","size":"5000000000","trashed":false,"modifiedTime":"2026-09-08T01:02:03.000Z"}]}"""
        ))
        val transport = object : HttpTransport() {
            override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
                urls += url
                return object : MockLowLevelHttpRequest(url) {
                    override fun execute(): LowLevelHttpResponse = MockLowLevelHttpResponse()
                        .setContentType("application/json").setContent(responses.removeFirst())
                }
            }
        }
        val client = Drive.Builder(transport, GoogleApiClient.jsonFactory, GoogleApiClient.initializer("test-token"))
            .setApplicationName("ManyDrive tests").build()
        val files = DriveApi.list(client.files().list().setQ("trashed = false"))
        assertEquals(2, files.size)
        assertTrue(files[0].isFolder)
        assertEquals(listOf("root"), files[0].parents)
        assertEquals(5_000_000_000L, files[1].size)
        assertNotNull(files[1].modifiedTime)
        assertEquals(2, urls.size)
        val lastUrl = com.google.api.client.http.GenericUrl(urls.last())
        assertEquals("next +/", lastUrl.getFirst("pageToken"))
        assertEquals("true", lastUrl.getFirst("supportsAllDrives"))
        assertEquals("true", lastUrl.getFirst("includeItemsFromAllDrives"))
    }

    @Test fun sdkTunnelsPatchOnAndroidTransportAndKeepsTokensSeparate() {
        val first = DriveApi.client("first-token").files().update("file-id", File().setName("new name"))
            .buildHttpRequest()
        val second = DriveApi.client("second-token").files().get("file-id").buildHttpRequest()
        assertEquals("POST", first.requestMethod)
        assertEquals("PATCH", first.headers.getFirstHeaderStringValue("X-HTTP-Method-Override"))
        assertEquals("Bearer first-token", first.headers.authorization)
        assertEquals("Bearer second-token", second.headers.authorization)
        assertEquals(15_000, first.connectTimeout)
        assertEquals(120_000, first.readTimeout)
        assertFalse(first.isLoggingEnabled)
    }
}
