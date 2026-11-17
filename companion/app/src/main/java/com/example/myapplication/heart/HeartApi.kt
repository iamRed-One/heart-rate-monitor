package com.example.myapplication.heart

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class HeartAccount(val id: String = "", val email: String = "", val csrf: String = "")
class ApiFailure(val status: Int, message: String): Exception(message)

/** Native client for the same cookie/CSRF account API as the web viewer. Secrets stay in memory. */
object HeartApi {
    var server = "http://127.0.0.1:8000"
    private val cookies = mutableListOf<Cookie>()
    private val mutable = MutableStateFlow(HeartAccount())
    val account = mutable.asStateFlow()
    private val http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .cookieJar(object: CookieJar {
            override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) = synchronized(cookies) {
                received.forEach { next -> cookies.removeAll { it.name == next.name && it.domain == next.domain }; cookies.add(next) }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) { cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() } }
        }).build()

    suspend fun call(path: String, method: String = "GET", body: JSONObject? = null, bearer: String? = null): JSONObject =
        execute(path, method, if (method in listOf("GET", "DELETE")) null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()), bearer)

    private suspend fun execute(path: String, method: String, body: RequestBody?, token: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(server.trimEnd('/') + "/api" + path)
            .header("Origin", server.trimEnd('/'))
            .apply {
                if (mutable.value.csrf.isNotEmpty()) header("X-CSRF-Token", mutable.value.csrf)
                if (token != null) header("Authorization", "Bearer $token")
            }.method(method, body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject().put("items", JSONArray(text)) }
            if (!response.isSuccessful) {
                if (response.code == 401 && token == null && !path.startsWith("/auth/")) clear()
                throw ApiFailure(response.code, json.optString("message").ifBlank { "Request failed (${response.code}). Check the details and try again." })
            }
            json
        }
    }

    suspend fun signIn(email: String, password: String) {
        val data = call("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password))
        val user = data.getJSONObject("user")
        mutable.value = HeartAccount(user.getString("id"), user.getString("email"), data.getString("csrf_token"))
    }
    suspend fun register(email: String, password: String) { call("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password)) }
    suspend fun signOut() { try { call("/auth/logout", "POST") } finally { clear() } }
    private fun clear() { synchronized(cookies) { cookies.clear() }; mutable.value = HeartAccount() }

    suspend fun preview(bytes: ByteArray, label: String, synthetic: Boolean): JSONObject {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("label", label.trim()).addFormDataPart("source_type", if (synthetic) "synthetic" else "recorded")
            .addFormDataPart("file", "heart-rate.csv", bytes.toRequestBody("text/csv".toMediaType())).build()
        return execute("/imports/preview", "POST", body)
    }
}
