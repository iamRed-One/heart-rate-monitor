package com.example.myapplication

import com.example.myapplication.heart.restoredSessionCookies
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SessionCookiesTest {
    private val expiry = 2_000_000_000_000L
    private fun cookie(domain: String = "api.example.com") = Cookie.Builder()
        .name("hr_session").value("sample-token").domain(domain).path("/")
        .secure().httpOnly().expiresAt(expiry).build().toString()

    @Test fun unexpiredCookieRestoresOnlyForItsHttpsServer() {
        val restored = restoredSessionCookies("https://api.example.com".toHttpUrl(), listOf(cookie()), expiry - 1)
        assertEquals("sample-token", restored.single().value)
        assertTrue(restored.single().httpOnly)
        assertTrue(restoredSessionCookies("http://api.example.com".toHttpUrl(), listOf(cookie()), expiry - 1).isEmpty())
    }

    @Test fun expiredCookieCannotRestoreAnAccount() {
        assertTrue(restoredSessionCookies("https://api.example.com".toHttpUrl(), listOf(cookie()), expiry).isEmpty())
    }

    @Test fun anotherServersCookieIsRejected() {
        assertTrue(restoredSessionCookies("https://api.example.com".toHttpUrl(), listOf(cookie("other.example.com")), expiry - 1).isEmpty())
    }
}
