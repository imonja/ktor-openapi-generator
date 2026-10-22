package com.papsign.ktor.openapigen.parameters.validation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParameterValidationOpenAPITest {
    @Test
    fun `should generate OpenAPI schema with parameter validation constraints`() =
        testApplication {
            application { configureParameterValidation() }

            client.get("http://localhost/openapi.json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val openApiSpec = bodyAsText()

                // Check query parameter validation constraints
                assertTrue(openApiSpec.contains("\"minimum\" : 0"), "Query param should have minimum constraint")
                assertTrue(openApiSpec.contains("\"maximum\" : 100"), "Query param should have maximum constraint")
                assertTrue(openApiSpec.contains("\"minLength\" : 3"), "String param should have minLength constraint")
                assertTrue(openApiSpec.contains("\"maxLength\" : 50"), "String param should have maxLength constraint")

                // Check path parameter validation constraints
                assertTrue(openApiSpec.contains("\"minimum\" : 1"), "Path param should have minimum constraint")
                assertTrue(openApiSpec.contains("\"maxLength\" : 20"), "Path string should have maxLength constraint")

                // Verify parameter names are included
                assertTrue(openApiSpec.contains("\"name\" : \"count\""), "Count parameter should be present")
                assertTrue(openApiSpec.contains("\"name\" : \"name\""), "Name parameter should be present")
                assertTrue(openApiSpec.contains("\"name\" : \"id\""), "ID parameter should be present")
                assertTrue(openApiSpec.contains("\"name\" : \"category\""), "Category parameter should be present")
            }
        }
}
