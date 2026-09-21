package com.papsign.ktor.openapigen.parameters.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderParameterValidationTest {

    @Test
    fun `accepts valid headers`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated/headers") {
            header("User-Agent", "Mozilla/5.0 (Test)")
            header("Request-Id", "123456")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Header processing success"))
            assertTrue(response.contains("Mozilla/5.0 (Test)"))
            assertTrue(response.contains("123456"))
        }
    }

    @Test
    fun `rejects a user agent shorter than the minimum`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated/headers") {
            header("User-Agent", "Test") // Only 4 chars, min is 5
            header("Request-Id", "123456")
        }.apply {
            assertTrue(status.value >= 400, "Should return error for User-Agent length < 5, got $status")
        }
    }

    @Test
    fun `rejects a user agent longer than the maximum`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated/headers") {
            header("User-Agent", "A".repeat(110)) // Over 100 chars
            header("Request-Id", "123456")
        }.apply {
            assertTrue(status.value >= 400, "Should return error for User-Agent length > 100, got $status")
        }
    }
}
