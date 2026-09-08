package com.starfall.gsadrive.data

import org.json.JSONObject

/** Uploads media using the append-only Google Photos Library scope. */
object PhotosApi {
    fun createAlbum(accessToken: String, title: String): String {
        val body = JSONObject().put("album", JSONObject().put("title", title))
        return JSONObject(GoogleApiClient.request(accessToken, "POST",
            "https://photoslibrary.googleapis.com/v1/albums", body.toString().toByteArray()).decodeToString()).getString("id")
    }

    fun upload(accessToken: String, file: java.io.File, filename: String, mimeType: String, albumId: String?) {
        val request = GoogleApiClient.transport.createRequestFactory(GoogleApiClient.initializer(accessToken, 300_000))
            .buildPostRequest(com.google.api.client.http.GenericUrl("https://photoslibrary.googleapis.com/v1/uploads"),
                com.google.api.client.http.FileContent("application/octet-stream", file))
        request.headers.set("X-Goog-Upload-Protocol", "raw")
        request.headers.set("X-Goog-Upload-Content-Type", mimeType)
        val response = request.execute()
        val uploadToken = try { response.parseAsString().trim() } finally { response.disconnect() }
        require(uploadToken.isNotEmpty()) { "Google Photos không trả về upload token." }
        val body = JSONObject().put("newMediaItems", org.json.JSONArray().put(
            JSONObject().put("simpleMediaItem", JSONObject().put("uploadToken", uploadToken).put("fileName", filename))))
        albumId?.let { body.put("albumId", it) }
        val result = JSONObject(GoogleApiClient.request(accessToken, "POST",
            "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate", body.toString().toByteArray()).decodeToString())
            .getJSONArray("newMediaItemResults").getJSONObject(0)
        check(result.optJSONObject("status")?.optInt("code", 0) in listOf(null, 0) && result.has("mediaItem")) {
            result.optJSONObject("status")?.optString("message") ?: "Không thể tạo mục Google Photos."
        }
    }
}
