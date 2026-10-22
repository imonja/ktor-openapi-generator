package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptionalRequestBodyValidationTest {
    @Test
    fun `accepts valid optional fields`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/optional") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "requiredName": "John",
                            "optionalNickname": "Johnny",
                            "optionalAge": 25,
                            "optionalSsn": "123-45-6789"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val response = bodyAsText()
                    assertTrue(response.contains("Optional validation successful"))
                }
        }

    @Test
    fun `accepts omitted optional fields`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/optional") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "requiredName": "John"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }
        }

    @Test
    fun `rejects a short optional nickname`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/optional") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "requiredName": "John",
                            "optionalNickname": "Jo"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for optional nickname length < 3, got $status")
                }
        }

    @Test
    fun `rejects a negative optional age`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/optional") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "requiredName": "John",
                            "optionalAge": -5
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for negative optional age, got $status")
                }
        }

    @Test
    fun `rejects an invalid optional SSN`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/optional") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "requiredName": "John",
                            "optionalSsn": "123456789"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid SSN format, got $status")
                }
        }
}
