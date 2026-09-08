package com.starfall.gsadrive.data

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String = "us-east-1"
)

/** S3/MinIO-compatible browser using AWS Signature V4 via the official Kotlin SDK. */
object S3Api {
    suspend fun list(config: S3Config, prefix: String = ""): List<DriveFile> = S3Client {
        region = config.region.ifBlank { "us-east-1" }
        endpointUrl = Url.parse(config.endpoint)
        forcePathStyle = true
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = config.accessKey
            secretAccessKey = config.secretKey
        }
    }.use { client ->
        val response = client.listObjectsV2 {
            bucket = config.bucket
            this.prefix = prefix
            delimiter = "/"
            maxKeys = 1_000
        }
        val folders = response.commonPrefixes.orEmpty().mapNotNull { item -> item.prefix }.map { key ->
            DriveFile(id = key, name = key.removePrefix(prefix).removeSuffix("/"), mimeType = "application/vnd.google-apps.folder", modifiedTime = null)
        }
        val objects = response.contents.orEmpty().filter { it.key != prefix }.mapNotNull { item ->
            item.key?.let { key ->
                DriveFile(id = key, name = key.removePrefix(prefix), mimeType = guessMimeType(key), modifiedTime = item.lastModified?.toString(), size = item.size)
            }
        }
        folders + objects
    }

    private fun guessMimeType(key: String): String = when (key.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
