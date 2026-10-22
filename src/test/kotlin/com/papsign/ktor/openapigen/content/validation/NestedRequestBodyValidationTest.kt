package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NestedRequestBodyValidationTest {
    @Test
    fun `accepts valid nested objects and collections`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/nested") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "firstName": "John",
                            "lastName": "Doe",
                            "address": {
                                "street": "123 Main St",
                                "zipCode": "12345",
                                "city": "New York"
                            },
                            "alternateAddresses": [
                                {
                                    "street": "456 Oak Ave",
                                    "zipCode": "67890-1234",
                                    "city": "Los Angeles"
                                }
                            ]
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val response = bodyAsText()
                    assertTrue(response.contains("Nested validation successful"))
                }
        }

    @Test
    fun `rejects an invalid ZIP in a nested object`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/nested") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "firstName": "John",
                            "lastName": "Doe",
                            "address": {
                                "street": "123 Main St",
                                "zipCode": "invalid-zip",
                                "city": "New York"
                            }
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid ZIP format, got $status")
                }
        }

    @Test
    fun `rejects an invalid street in a nested collection`() =
        testApplication {
            application { configureRequestBodyValidation() }

            client
                .post("http://localhost/request-body-validation/nested") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "firstName": "John",
                            "lastName": "Doe",
                            "address": {
                                "street": "123 Main St",
                                "zipCode": "12345",
                                "city": "New York"
                            },
                            "alternateAddresses": [
                                {
                                    "street": "A",
                                    "zipCode": "67890",
                                    "city": "Los Angeles"
                                }
                            ]
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    assertTrue(status.value >= 400, "Should return error for invalid street length in array, got $status")
                }
        }
}
