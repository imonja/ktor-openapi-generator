package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringRequestBodyValidationTest {
    @Test
    fun `accepts valid string fields`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/string") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John",
                            "description": "Valid description",
                            "username": "john123",
                            "email": "john@example.com",
                            "category": " ELECTRONICS "
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val response = bodyAsText()
                    assertTrue(response.contains("String validation successful"))
                }
        }

    @Test
    fun `rejects a name shorter than the minimum`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/string") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Jo",
                            "description": "Valid description",
                            "username": "john123",
                            "email": "john@example.com",
                            "category": "electronics"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for name length < 3, got $status")
                }
        }

    @Test
    fun `rejects an invalid email`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/string") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John",
                            "description": "Valid description",
                            "username": "john123",
                            "email": "invalid-email",
                            "category": "electronics"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid email format, got $status")
                }
        }

    @Test
    fun `rejects a description longer than the maximum`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/string") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "John",
                            "description": "${"A".repeat(60)}",
                            "username": "john123",
                            "email": "john@example.com",
                            "category": "electronics"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for description length > 50, got $status")
                }
        }

    @Test
    fun `accepts string boundary values`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/string") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "ABC",
                            "description": "${"A".repeat(50)}",
                            "username": "abcde",
                            "email": "a@b.co",
                            "category": "test"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status, "Boundary values should be accepted")
                }
        }
}
