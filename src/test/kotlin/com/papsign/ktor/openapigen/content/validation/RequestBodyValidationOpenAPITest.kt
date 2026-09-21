package com.papsign.ktor.openapigen.content.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestBodyValidationOpenAPITest {

    @Test
    fun `should generate OpenAPI schema with RequestBody validation constraints for strings`() = testApplication {
        application { configureRequestBodyValidation() }

        client.get("http://localhost/openapi.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            val openApiSpec = bodyAsText()

            // Check string validation constraints are present in the schema
            assertTrue(openApiSpec.contains("\"minLength\" : 3"), "String should have minLength constraint")
            assertTrue(openApiSpec.contains("\"maxLength\" : 50"), "String should have maxLength constraint")
            assertTrue(openApiSpec.contains("\"minLength\" : 5"), "Username should have minLength constraint")
            assertTrue(openApiSpec.contains("\"maxLength\" : 20"), "Username should have maxLength constraint")

            // Check regex pattern is present
            assertTrue(openApiSpec.contains("\"pattern\""), "Email should have regex pattern")

            // Verify request body structure is included
            assertTrue(openApiSpec.contains("\"requestBody\""), "Should contain requestBody definition")
            assertTrue(openApiSpec.contains("\"StringValidatedRequest\""), "Should reference StringValidatedRequest schema")
        }
    }

    @Test
    fun `should generate OpenAPI schema with phone number validation constraints`() = testApplication {
        application { configureRequestBodyValidation() }

        client.get("http://localhost/openapi.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            val openApiSpec = bodyAsText()

            // Check phone validation constraints are present in the schema
            assertTrue(openApiSpec.contains("\"PhoneValidatedRequest\""), "Should reference PhoneValidatedRequest schema")
            assertTrue(openApiSpec.contains("\"pattern\""), "Phone should have regex pattern constraint")

            // Verify phone number pattern is complex (contains alternations)
            assertTrue(openApiSpec.contains("\\\\+"), "Phone pattern should contain international prefix")

            // Verify request body structure is included
            assertTrue(openApiSpec.contains("\"requestBody\""), "Should contain requestBody definition")
            assertTrue(openApiSpec.contains("\"minLength\" : 2"), "Name should have minLength constraint")
        }
    }

    @Test
    fun `should generate OpenAPI schema with RequestBody validation constraints for numbers`() = testApplication {
        application { configureRequestBodyValidation() }

        client.get("http://localhost/openapi.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            val openApiSpec = bodyAsText()

            // Check number validation constraints
            assertTrue(openApiSpec.contains("\"minimum\" : 0"), "Age should have minimum constraint")
            assertTrue(openApiSpec.contains("\"maximum\" : 120"), "Age should have maximum constraint")
            assertTrue(openApiSpec.contains("\"minimum\" : 1"), "Rating should have minimum constraint")
            assertTrue(openApiSpec.contains("\"maximum\" : 10"), "Rating should have maximum constraint")

            // Check float constraints
            assertTrue(openApiSpec.contains("\"minimum\" : 0.0"), "Percentage should have float minimum")
            assertTrue(openApiSpec.contains("\"maximum\" : 100.0"), "Percentage should have float maximum")
        }
    }
}
