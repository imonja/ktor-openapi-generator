package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneRequestBodyValidationTest {
    @Test
    fun `accepts an international phone number`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John Doe",
                            "phoneNumber": "+1234567890",
                            "email": "john@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val response = bodyAsText()
                    assertTrue(response.contains("Phone validation successful"))
                    assertTrue(response.contains("+1234567890"))
                }
        }

    @Test
    fun `accepts a US phone number with parentheses`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Jane Smith",
                            "phoneNumber": "(123) 456-7890",
                            "email": "jane@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }
        }

    @Test
    fun `accepts a US phone number with hyphens`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Bob Johnson",
                            "phoneNumber": "123-456-7890",
                            "email": "bob@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }
        }

    @Test
    fun `accepts a US international phone number`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Alice Cooper",
                            "phoneNumber": "+12345678901",
                            "email": "alice@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }
        }

    @Test
    fun `rejects a phone number that is too short`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John Doe",
                            "phoneNumber": "123",
                            "email": "john@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid phone format (too short), got $status")
                }
        }

    @Test
    fun `rejects a phone number containing letters`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John Doe",
                            "phoneNumber": "123-ABC-7890",
                            "email": "john@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid phone format (letters), got $status")
                }
        }

    @Test
    fun `rejects a phone number with two plus signs`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John Doe",
                            "phoneNumber": "++1234567890",
                            "email": "john@example.com"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid phone format (double plus), got $status")
                }
        }

    @Test
    fun `rejects an invalid email alongside a valid phone number`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/phone") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John Doe",
                            "phoneNumber": "+1234567890",
                            "email": "invalid-email"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid email format, got $status")
                }
        }
}
