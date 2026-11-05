package me.nomi.urdutyper.presentation.ui.type.studio

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Partial bytes remain private; only a complete checksum-verified file can be installed. */
internal object ResumableModelDownload {
    private const val MAX = 600L*1024*1024
    fun transfer(source: URL, partial: File, checksum: String, cancelled: () -> Boolean,
                 progress: (Long,Long) -> Unit, open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): File {
        val metadata = File(partial.path+".validator")
        fun checkCancel() { if (cancelled()) throw CancellationException("Download paused; resume when ready") }
        fun verify(): Boolean {
            val hash = MessageDigest.getInstance("SHA-1")
            partial.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { checkCancel(); val n=input.read(buffer); if(n<0) break; hash.update(buffer,0,n) } }
            return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == checksum
        }
        var url = source
        repeat(10) {
            checkCancel(); require(url.protocol == "https") { "Model download needs HTTPS" }
            val offset = if (partial.isFile) partial.length() else 0L
            val connection = open(url)
            connection.connectTimeout=20000; connection.readTimeout=20000; connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0) {
                connection.setRequestProperty("Range","bytes=$offset-")
                if(metadata.isFile) connection.setRequestProperty("If-Range",metadata.readText().take(512))
            }
            try {
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) { url=URL(url,connection.getHeaderField("Location") ?: error("Missing download redirect")); return@repeat }
                if(code==416 && offset>0) {
                    if(verify()) return partial
                    partial.delete(); metadata.delete(); url=source; return@repeat
                }
                check(code==200 || code==206) { "Download interrupted ($code). Tap Resume download or import the model." }
                val range = connection.getHeaderField("Content-Range")?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it) }
                val append = code==206
                val start=if(append) offset else 0L
                val total=if(append) range?.groupValues?.get(3)?.toLongOrNull() ?: error("Invalid resume response") else connection.contentLengthLong
                if(append) require(range!=null && range.groupValues[1].toLongOrNull()==offset && range.groupValues[2].toLong()>=offset && range.groupValues[2].toLong()<total) { "Server returned an invalid download range" }
                require(total<=MAX && start<=MAX) { "Model exceeds 600 MiB" }
                check(partial.parentFile!!.usableSpace > (if(total>0) total-start else 16L*1024*1024)+16L*1024*1024) { "Not enough free storage for the model" }
                val validator=connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") } ?: connection.getHeaderField("Last-Modified")
                if(validator!=null) metadata.writeText(validator.take(512)) else if(!append) metadata.delete()
                var copied=start
                connection.inputStream.use { input -> java.io.FileOutputStream(partial,append).use { output ->
                    try {
                        val buffer=ByteArray(65536); progress(copied,total)
                        while(true) { checkCancel(); val n=input.read(buffer); if(n<0) break; copied+=n; require(copied<=MAX && (total<0 || copied<=total)) { "Invalid model length" }; output.write(buffer,0,n); progress(copied,total) }
                    } finally { output.fd.sync() }
                } }
                check(total<0 || copied==total) { "Download paused before completion; tap Resume download" }
                checkCancel()
                if(!verify()) { partial.delete(); metadata.delete(); error("Model checksum failed. Tap Download to start a clean copy.") }
                metadata.delete(); return partial
            } finally { connection.disconnect() }
        }
        error("Could not resume model; try again later")
    }
}
