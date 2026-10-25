package me.nomi.urdutyper.data.source

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import me.nomi.urdutyper.domain.entity.StorageUsage
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Deployed Worker URL (Cloudflare dashboard deploy, KV variant, verified
// 2026-10-25: no-token GET /v1/posters returns 401 "Missing Firebase ID token").
const val POSTER_WORKER_BASE_URL =
    "https://urdu-typer-posters.sadique6571.workers.dev"

// One entry of the Worker's authenticated poster list.
data class PosterInfo(
    val name: String,
    val url: String,
    val size: Long
)

/**
 * Poster backup transport for Cloudflare R2 (via the poster Worker).
 * Firebase login stays on Firebase; only the ID token is sent.
 * Upload/list/delete methods join here when the backup migration lands.
 */
class PosterBackupClient(
    private val workerBaseUrl: String,
    private val auth: FirebaseAuth
) {
    init {
        require(workerBaseUrl.startsWith("https://")) { "Poster Worker must use HTTPS" }
    }

    private suspend fun authToken(): String =
        auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Firebase login required for backups")

    private fun contentTypeFor(filename: String): String =
        when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

    suspend fun listPosters(): List<PosterInfo> = withContext(Dispatchers.IO) {
        val token = authToken()
        val connection =
            URL("${workerBaseUrl.trimEnd('/')}/v1/posters").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $status"
                throw IllegalStateException("Poster list failed ($status): " + message.take(250))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val posters = JSONObject(body).optJSONArray("posters") ?: return@withContext emptyList()
            (0 until posters.length()).map { i ->
                val item = posters.getJSONObject(i)
                PosterInfo(
                    name = item.optString("name"),
                    url = item.optString("url"),
                    size = item.optLong("size")
                )
            }.filter { it.name.isNotEmpty() && it.url.isNotEmpty() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun uploadPoster(file: File) = withContext(Dispatchers.IO) {
        val token = authToken()
        val bytes = file.readBytes()
        require(bytes.size <= 10_000_000) { "Poster is larger than 10 MB" }
        // App poster filenames are safe stems (e.g. UT20261024_123456.jpg);
        // the Worker validates the name and rejects anything else.
        val connection =
            URL("${workerBaseUrl.trimEnd('/')}/v1/posters/${file.name}").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", contentTypeFor(file.name))
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $status"
                throw IllegalStateException("Poster upload failed ($status): " + message.take(250))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun deletePoster(filename: String) = withContext(Dispatchers.IO) {
        val token = authToken()
        val connection =
            URL("${workerBaseUrl.trimEnd('/')}/v1/posters/$filename").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $status"
                throw IllegalStateException("Poster delete failed ($status): " + message.take(250))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val token = authToken()
        val connection =
            URL("${workerBaseUrl.trimEnd('/')}/v1/storage-usage").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $status"
                throw IllegalStateException("Storage usage failed ($status): " + message.take(250))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            StorageUsage(
                usedBytes = json.optLong("usedBytes"),
                totalBytes = json.optLong("totalBytes"),
                limitBytes = json.optLong("limitBytes")
            )
        } finally {
            connection.disconnect()
        }
    }
}
