import TestServer.setupBaseTestServer
import com.papsign.ktor.openapigen.annotations.mapping.OpenAPIName
import com.papsign.ktor.openapigen.annotations.parameters.HeaderParam
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.annotations.type.number.integer.max.Max
import com.papsign.ktor.openapigen.annotations.type.number.integer.min.Min
import com.papsign.ktor.openapigen.annotations.type.string.length.Length
import com.papsign.ktor.openapigen.annotations.type.string.length.MaxLength
import com.papsign.ktor.openapigen.annotations.type.string.length.MinLength
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParameterValidationTest {

    // Test data classes with validated parameters
    data class ValidatedQueryParams(
        @QueryParam("Integer count with min/max validation")
        @Min(0)
        @Max(100)
        val count: Int,

        @QueryParam("String name with length validation")
        @Length(min = 3, max = 50)
        val name: String,

        @QueryParam("Optional string with min length")
        @MinLength(2)
        val tag: String? = null
    )

    data class ValidatedPathParams(
        @PathParam("ID with minimum value")
        @Min(1)
        val id: Long,

        @PathParam("Category with max length")
        @MaxLength(20)
        val category: String
    )

    data class ValidatedHeaderParams(
        @HeaderParam("User agent header")
        @OpenAPIName("User-Agent")
        @Length(min = 5, max = 100)
        val userAgent: String,

        @HeaderParam("Request ID header")
        @OpenAPIName("Request-Id")
        val requestId: String // Keep this simple for now
    )

    data class ValidationResponse(
        val message: String,
        val count: Int? = null,
        val name: String? = null,
        val id: Long? = null,
        val category: String? = null
    )

    // API routes with validated parameters
    fun NormalOpenAPIRoute.validationTestRoutes() {
        route("/validated") {
            // Query parameter validation
            get<ValidatedQueryParams, ValidationResponse>(
                info(
                    summary = "Test query parameter validation",
                    description = "Endpoint with validated query parameters"
                )
            ) { params ->
                respond(
                    ValidationResponse(
                        message = "Query validation success",
                        count = params.count,
                        name = params.name
                    )
                )
            }

            // Path parameter validation
            route("/item/{id}/{category}") {
                get<ValidatedPathParams, ValidationResponse>(
                    info(
                        summary = "Test path parameter validation",
                        description = "Endpoint with validated path parameters"
                    )
                ) { params ->
                    respond(
                        ValidationResponse(
                            message = "Path validation success",
                            id = params.id,
                            category = params.category
                        )
                    )
                }
            }

            // Header parameter validation (basic test without validation annotations)
            route("/headers") {
                get<ValidatedHeaderParams, ValidationResponse>(
                    info(
                        summary = "Test header parameter processing",
                        description = "Endpoint with basic header parameters"
                    )
                ) { params ->
                    respond(
                        ValidationResponse(
                            message = "Header processing success: ${params.userAgent}, ${params.requestId}"
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `should generate OpenAPI schema with parameter validation constraints`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                validationTestRoutes()
            }
        }

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

    @Test
    fun `should validate query parameters at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                validationTestRoutes()
            }
        }

        // Valid request should work
        client.get("http://localhost/validated?count=50&name=ValidName").apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Query validation success"))
            assertTrue(response.contains("\"count\" : 50"))
            assertTrue(response.contains("\"name\" : \"ValidName\""))
        }

        // Invalid count (too high) should fail
        client.get("http://localhost/validated?count=150&name=ValidName").apply {
            assertTrue(status.value >= 400, "Should return error for count > 100")
        }

        // Invalid count (negative) should fail
        client.get("http://localhost/validated?count=-5&name=ValidName").apply {
            assertTrue(status.value >= 400, "Should return error for negative count")
        }

        // Invalid name (too short) should fail
        client.get("http://localhost/validated?count=50&name=AB").apply {
            assertTrue(status.value >= 400, "Should return error for name length < 3")
        }

        // Invalid name (too long) should fail
        client.get("http://localhost/validated?count=50&name=${"A".repeat(60)}").apply {
            assertTrue(status.value >= 400, "Should return error for name length > 50")
        }
    }

    @Test
    fun `should validate path parameters at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                validationTestRoutes()
            }
        }

        // Valid request should work
        client.get("http://localhost/validated/item/123/electronics").apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Path validation success"))
            assertTrue(response.contains("\"id\" : 123"))
            assertTrue(response.contains("\"category\" : \"electronics\""))
        }

        // Invalid ID (too low) should fail
        client.get("http://localhost/validated/item/0/electronics").apply {
            assertTrue(status.value >= 400, "Should return error for id < 1")
        }

        // Invalid category (too long) should fail
        client.get("http://localhost/validated/item/123/${"A".repeat(25)}").apply {
            assertTrue(status.value >= 400, "Should return error for category length > 20")
        }
    }

    @Test
    fun `should validate header parameters at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                validationTestRoutes()
            }
        }

        // Valid request should work
        client.get("http://localhost/validated/headers") {
            header("User-Agent", "Mozilla/5.0 (Test)")
            header("Request-Id", "123456")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Header processing success"))
            assertTrue(response.contains("Mozilla/5.0 (Test)"))
            assertTrue(response.contains("123456"))
        }

        // Invalid User-Agent (too short) should fail
        client.get("http://localhost/validated/headers") {
            header("User-Agent", "Test") // Only 4 chars, min is 5
            header("Request-Id", "123456")
        }.apply {
            assertTrue(status.value >= 400, "Should return error for User-Agent length < 5, got $status")
        }

        // Invalid User-Agent (too long) should fail
        client.get("http://localhost/validated/headers") {
            header("User-Agent", "A".repeat(110)) // Over 100 chars
            header("Request-Id", "123456")
        }.apply {
            assertTrue(status.value >= 400, "Should return error for User-Agent length > 100, got $status")
        }
    }

    @Test
    fun `should handle optional validated parameters correctly`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                validationTestRoutes()
            }
        }

        // Without optional parameter should work
        client.get("http://localhost/validated?count=50&name=ValidName").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // With valid optional parameter should work
        client.get("http://localhost/validated?count=50&name=ValidName&tag=ValidTag").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // With invalid optional parameter (too short) should fail
        client.get("http://localhost/validated?count=50&name=ValidName&tag=A").apply {
            assertTrue(status.value >= 400, "Should return error for optional tag length < 2")
        }
    }
}
