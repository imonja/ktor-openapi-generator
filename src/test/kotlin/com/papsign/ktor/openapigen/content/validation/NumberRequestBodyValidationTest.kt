package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberRequestBodyValidationTest {
    @Test
    fun `accepts valid numeric fields`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 25,
                            "rating": 8,
                            "percentage": 85.5,
                            "weight": 0.75
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val response = bodyAsText()
                    assertTrue(response.contains("Number validation successful"))
                }
        }

    @Test
    fun `rejects a negative age`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": -5,
                            "rating": 8,
                            "percentage": 85.5,
                            "weight": 0.75
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for negative age, got $status")
                }
        }

    @Test
    fun `rejects an age above the maximum`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 150,
                            "rating": 8,
                            "percentage": 85.5,
                            "weight": 0.75
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for age > 120, got $status")
                }
        }

    @Test
    fun `rejects a rating outside the range`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 25,
                            "rating": 15,
                            "percentage": 85.5,
                            "weight": 0.75
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for rating > 10, got $status")
                }
        }

    @Test
    fun `rejects a percentage above the maximum`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 25,
                            "rating": 8,
                            "percentage": 150.0,
                            "weight": 0.75
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for percentage > 100.0, got $status")
                }
        }

    @Test
    fun `accepts minimum numeric boundary values`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 0,
                            "rating": 1,
                            "percentage": 0.0,
                            "weight": 0.0
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status, "Minimum boundary values should be accepted")
                }
        }

    @Test
    fun `accepts maximum numeric boundary values`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/number") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "age": 120,
                            "rating": 10,
                            "percentage": 100.0,
                            "weight": 1.0
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status, "Maximum boundary values should be accepted")
                }
        }
}
