package me.nomi.urdutyper.data.repository
import me.nomi.urdutyper.domain.utils.Result
suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
    return try {
        val result = apiCall()
        Result.Success(result)
    } catch (e: Exception) {
        Result.Error(e)
    }
}