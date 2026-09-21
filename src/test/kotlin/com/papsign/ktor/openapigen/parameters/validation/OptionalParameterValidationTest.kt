package com.papsign.ktor.openapigen.parameters.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptionalParameterValidationTest {

    @Test
    fun `accepts an omitted optional parameter`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=ValidName").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun `accepts a valid optional parameter`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=ValidName&tag=ValidTag").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun `rejects a short optional parameter`() = testApplication {
        application { configureParameterValidation() }

        client.get("http://localhost/validated?count=50&name=ValidName&tag=A").apply {
            assertTrue(status.value >= 400, "Should return error for optional tag length < 2")
        }
    }
}
