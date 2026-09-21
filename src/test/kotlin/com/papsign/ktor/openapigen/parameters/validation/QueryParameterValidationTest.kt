package com.papsign.ktor.openapigen.parameters.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryParameterValidationTest {

    @Test
    fun `accepts valid query parameters`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=ValidName").apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Query validation success"))
            assertTrue(response.contains("\"count\" : 50"))
            assertTrue(response.contains("\"name\" : \"ValidName\""))
        }
    }

    @Test
    fun `rejects a count above the maximum`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=150&name=ValidName").apply {
            assertTrue(status.value >= 400, "Should return error for count > 100")
        }
    }

    @Test
    fun `rejects a negative count`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=-5&name=ValidName").apply {
            assertTrue(status.value >= 400, "Should return error for negative count")
        }
    }

    @Test
    fun `rejects a name shorter than the minimum`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=AB").apply {
            assertTrue(status.value >= 400, "Should return error for name length < 3")
        }
    }

    @Test
    fun `rejects a name longer than the maximum`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=${"A".repeat(60)}").apply {
            assertTrue(status.value >= 400, "Should return error for name length > 50")
        }
    }
}
