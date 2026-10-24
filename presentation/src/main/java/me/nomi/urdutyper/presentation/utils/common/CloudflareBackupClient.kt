package me.nomi.urdutyper.presentation.utils.common

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Private R2 backup transport. Configure the Worker URL in your application, never an R2 key.
 * Firebase login stays on Firebase. This class does not yet schedule background sync.
 */
class CloudflareBackupClient(
    private val workerBaseUrl: String,
    private val auth: FirebaseAuth
) {
    init {
        require(workerBaseUrl.startsWith("https://")) { "Cloudflare Worker must use HTTPS" }
    }

    suspend fun uploadProject(projectId: String, json: String) {
        require(json.isNotBlank())
        request(projectId, "document", "PUT", "application/json", json.toByteArray(Charsets.UTF_8))
    }

    suspend fun downloadProject(projectId: String): String =
        request(projectId, "document", "GET").toString(Charsets.UTF_8)

    suspend fun uploadPreview(projectId: String, jpegBytes: ByteArray) {
        require(jpegBytes.isNotEmpty())
        request(projectId, "preview", "PUT", "image/jpeg", jpegBytes)
    }

    suspend fun downloadPreview(projectId: String): ByteArray =
        request(projectId, "preview", "GET")

    suspend fun deleteProjectDocument(projectId: String) {
        request(projectId, "document", "DELETE")
    }

    suspend fun deletePreview(projectId: String) {
        request(projectId, "preview", "DELETE")
    }

    private suspend fun request(
        projectId: String,
        kind: String,
        method: String,
        contentType: String? = null,
        data: ByteArray? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        require(projectId.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        val token = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Firebase login required for backups")
        val base = workerBaseUrl.trimEnd('/')
        val connection = URL("$base/v1/projects/$projectId/$kind").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (data != null) {
                require(data.size in 1..10_000_000) { "Backup exceeds 10 MB" }
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(data.size)
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(data) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $status"
                throw IllegalStateException("Backup request failed ($status): " + message.take(250))
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
