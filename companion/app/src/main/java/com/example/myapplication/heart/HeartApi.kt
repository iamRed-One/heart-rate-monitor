package com.example.myapplication.heart

import android.content.Context
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class HeartAccount(val id: String = "", val email: String = "", val csrf: String = "")
class ApiFailure(val status: Int, message: String): Exception(message)

/** Native cookie/CSRF client. Persist only the encrypted cookie; revalidate identity on launch. */
object HeartApi {
    var server = "https://heart-rate-monitor-q6mx.onrender.com"
        private set
    private val cookies = mutableListOf<Cookie>()
    private val mutable = MutableStateFlow(HeartAccount())
    val account = mutable.asStateFlow()
    private val authMutex = Mutex()
    private var store: SecureSessionStore? = null
    private val mutableRestoring = MutableStateFlow(false)
    val restoring = mutableRestoring.asStateFlow()

    @Synchronized fun initialize(context: Context) {
        if (store != null) return
        store = SecureSessionStore(context.applicationContext)
        mutableRestoring.value = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                authMutex.withLock {
                    val saved = store!!.read() ?: return@withLock
                    val origin = saved.optString("server").toHttpUrlOrNull()
                    if (origin == null || (origin.scheme != "https" &&
                        !(BuildConfig.DEBUG && origin.host in listOf("127.0.0.1", "localhost")))) {
                        clear(); return@withLock
                    }
                    val values = saved.optJSONArray("cookies") ?: run { clear(); return@withLock }
                    val restored = restoredSessionCookies(origin, List(values.length()) { values.getString(it) }, System.currentTimeMillis())
                    if (restored.isEmpty()) { clear(); return@withLock }
                    server = origin.toString().trimEnd('/')
                    synchronized(cookies) { cookies.clear(); cookies.addAll(restored) }
                    setAccount(call("/auth/me"))
                }
            } catch (_: Exception) {
                // A temporary network failure retains the encrypted cookie for the next launch.
            } finally { mutableRestoring.value = false }
        }
    }
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
        val client = if (path == "/auth/me" && mutableRestoring.value)
            http.newBuilder().callTimeout(90, TimeUnit.SECONDS).build() else http
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject().put("items", JSONArray(text)) }
            if (!response.isSuccessful) {
                if (response.code == 401 && token == null && !path.startsWith("/auth/login") && !path.startsWith("/auth/register")) clear()
                throw ApiFailure(response.code, json.optString("message").ifBlank { "Request failed (${response.code}). Check the details and try again." })
            }
            json
        }
    }

    private fun setAccount(data: JSONObject) {
        val user = data.getJSONObject("user")
        mutable.value = HeartAccount(user.getString("id"), user.getString("email"), data.getString("csrf_token"))
    }

    private fun selectServer(address: String) {
        if (server != address) { clear(); server = address }
    }

    suspend fun signIn(email: String, password: String, address: String = server) = withContext(Dispatchers.IO) {
        authMutex.withLock {
            selectServer(address)
            val data = call("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password))
            try {
                val saved = synchronized(cookies) { cookies.filter { it.expiresAt > System.currentTimeMillis() }.map { it.toString() } }
                check(saved.isNotEmpty()) { "The server did not return a session cookie." }
                checkNotNull(store) { "Session storage unavailable" }.write(JSONObject().put("server", server).put("cookies", JSONArray(saved)))
                setAccount(data)
            } catch (error: Exception) { clear(); throw error }
        }
    }
    suspend fun register(email: String, password: String, address: String = server) = withContext(Dispatchers.IO) {
        authMutex.withLock { selectServer(address); call("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password)); Unit }
    }
    suspend fun signOut() = withContext(Dispatchers.IO) {
        authMutex.withLock { try { call("/auth/logout", "POST") } finally { clear() }; Unit }
    }
    private fun clear() {
        synchronized(cookies) { cookies.clear() }
        store?.clear()
        mutable.value = HeartAccount()
    }

    suspend fun preview(bytes: ByteArray, label: String, synthetic: Boolean): JSONObject {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("label", label.trim()).addFormDataPart("source_type", if (synthetic) "synthetic" else "recorded")
            .addFormDataPart("file", "heart-rate.csv", bytes.toRequestBody("text/csv".toMediaType())).build()
        return execute("/imports/preview", "POST", body)
    }
}
