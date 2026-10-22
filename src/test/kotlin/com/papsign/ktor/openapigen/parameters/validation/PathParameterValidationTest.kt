package com.papsign.ktor.openapigen.parameters.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathParameterValidationTest {
    @Test
    fun `accepts valid path parameters`() =
        testApplication {
            application { configureParameterValidation() }

            client.get("http://localhost/validated/item/123/electronics").apply {
                assertEquals(HttpStatusCode.OK, status)
                val response = bodyAsText()
                assertTrue(response.contains("Path validation success"))
                assertTrue(response.contains("\"id\" : 123"))
                assertTrue(response.contains("\"category\" : \"electronics\""))
            }
        }

    @Test
    fun `rejects an ID below the minimum`() =
        testApplication {
            application { configureParameterValidation() }

            client.get("http://localhost/validated/item/0/electronics").apply {
                assertTrue(status.value >= 400, "Should return error for id < 1")
            }
        }

    @Test
    fun `rejects a category longer than the maximum`() =
        testApplication {
            application { configureParameterValidation() }

            client.get("http://localhost/validated/item/123/${"A".repeat(25)}").apply {
                assertTrue(status.value >= 400, "Should return error for category length > 20")
            }
        }
}
