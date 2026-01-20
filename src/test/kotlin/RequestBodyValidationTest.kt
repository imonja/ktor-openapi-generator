import TestServer.setupBaseTestServer
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.type.number.floating.clamp.FClamp
import com.papsign.ktor.openapigen.annotations.type.number.floating.max.FMax
import com.papsign.ktor.openapigen.annotations.type.number.floating.min.FMin
import com.papsign.ktor.openapigen.annotations.type.number.integer.clamp.Clamp
import com.papsign.ktor.openapigen.annotations.type.number.integer.max.Max
import com.papsign.ktor.openapigen.annotations.type.number.integer.min.Min
import com.papsign.ktor.openapigen.annotations.type.string.length.Length
import com.papsign.ktor.openapigen.annotations.type.string.length.MaxLength
import com.papsign.ktor.openapigen.annotations.type.string.length.MinLength
import com.papsign.ktor.openapigen.annotations.type.string.lowercase.LowerCase
import com.papsign.ktor.openapigen.annotations.type.string.pattern.RegularExpression
import com.papsign.ktor.openapigen.annotations.type.string.trim.Trim
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestBodyValidationTest {

    // Simple string validation request body
    @Request("Request body with string validations")
    data class StringValidatedRequest(
        @MinLength(3, "Name must be at least 3 characters")
        val name: String,

        @MaxLength(50)
        val description: String,

        @Length(5, 20)
        val username: String,

        @RegularExpression("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", "Invalid email format")
        val email: String,

        @Trim
        @LowerCase
        val category: String
    )

    // Number validation request body
    @Request("Request body with number validations")
    data class NumberValidatedRequest(
        @Min(0, "Age cannot be negative")
        @Max(120, "Age cannot exceed 120")
        val age: Int,

        @Clamp(1, 10, "Rating must be between 1 and 10")
        val rating: Int,

        @FMin(0.0)
        @FMax(100.0)
        val percentage: Float,

        @FClamp(0.0, 1.0, "Weight must be between 0 and 1")
        val weight: Double
    )

    // Nested object validation
    @Request("Address information")
    data class AddressRequest(
        @MinLength(2)
        val street: String,

        @RegularExpression("^[0-9]{5}(-[0-9]{4})?$", "Invalid ZIP code format")
        val zipCode: String,

        @MaxLength(50)
        val city: String
    )

    @Request("Request body with nested validation")
    data class NestedValidatedRequest(
        @MinLength(2)
        val firstName: String,

        @MinLength(2)
        val lastName: String,

        val address: AddressRequest,

        val alternateAddresses: List<AddressRequest>? = null
    )

    // Phone validation request body
    @Request("Request body with phone number validation")
    data class PhoneValidatedRequest(
        @MinLength(2)
        val name: String,

        @RegularExpression(
            "^(\\+[1-9]\\d{1,14}|\\([0-9]{3}\\)[\\s\\-]?[0-9]{3}[\\s\\-]?[0-9]{4}|[0-9]{3}[\\s\\-]?[0-9]{3}[\\s\\-]?[0-9]{4})$",
            "Invalid phone number format. Supported formats: +1234567890, (123) 456-7890, 123-456-7890"
        )
        val phoneNumber: String,

        @RegularExpression("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        val email: String
    )

    // Optional fields validation
    @Request("Request body with optional validated fields")
    data class OptionalValidatedRequest(
        @MinLength(3)
        val requiredName: String,

        @MinLength(3)
        val optionalNickname: String? = null,

        @Min(0)
        val optionalAge: Int? = null,

        @RegularExpression("^[0-9]{3}-[0-9]{2}-[0-9]{4}$")
        val optionalSsn: String? = null
    )

    @Response("Success response")
    data class ValidationResponse(
        val message: String,
        val receivedData: String? = null
    )

    // API routes for testing
    fun NormalOpenAPIRoute.requestBodyValidationRoutes() {
        route("/request-body-validation") {
            // String validation endpoint
            route("/string").post<Unit, ValidationResponse, StringValidatedRequest>(
                info(
                    summary = "Test string validation in request body",
                    description = "Endpoint that validates string fields in request body"
                )
            ) { _, request ->
                respond(
                    ValidationResponse(
                        message = "String validation successful",
                        receivedData = "name=${request.name}, username=${request.username}, email=${request.email}"
                    )
                )
            }

            // Number validation endpoint
            route("/number").post<Unit, ValidationResponse, NumberValidatedRequest>(
                info(
                    summary = "Test number validation in request body",
                    description = "Endpoint that validates number fields in request body"
                )
            ) { _, request ->
                respond(
                    ValidationResponse(
                        message = "Number validation successful",
                        receivedData = "age=${request.age}, rating=${request.rating}, percentage=${request.percentage}"
                    )
                )
            }

            // Nested validation endpoint
            route("/nested").post<Unit, ValidationResponse, NestedValidatedRequest>(
                info(
                    summary = "Test nested object validation in request body",
                    description = "Endpoint that validates nested objects in request body"
                )
            ) { _, request ->
                respond(
                    ValidationResponse(
                        message = "Nested validation successful",
                        receivedData = "${request.firstName} ${request.lastName} at ${request.address.street}, ${request.address.city}"
                    )
                )
            }

            // Phone validation endpoint
            route("/phone").post<Unit, ValidationResponse, PhoneValidatedRequest>(
                info(
                    summary = "Test phone number validation in request body",
                    description = "Endpoint that validates phone number formats in request body"
                )
            ) { _, request ->
                respond(
                    ValidationResponse(
                        message = "Phone validation successful",
                        receivedData = "name=${request.name}, phone=${request.phoneNumber}, email=${request.email}"
                    )
                )
            }

            // Optional fields validation endpoint
            route("/optional").post<Unit, ValidationResponse, OptionalValidatedRequest>(
                info(
                    summary = "Test optional field validation in request body",
                    description = "Endpoint that validates optional fields in request body"
                )
            ) { _, request ->
                respond(
                    ValidationResponse(
                        message = "Optional validation successful",
                        receivedData = "name=${request.requiredName}, nickname=${request.optionalNickname}, age=${request.optionalAge}"
                    )
                )
            }
        }
    }

    @Test
    fun `should generate OpenAPI schema with RequestBody validation constraints for strings`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

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
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

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
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

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

    @Test
    fun `should validate string fields in RequestBody at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Valid request should succeed
        client.post("http://localhost/request-body-validation/string") {
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
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("String validation successful"))
        }

        // Invalid name (too short) should fail
        client.post("http://localhost/request-body-validation/string") {
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
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for name length < 3, got $status")
        }

        // Invalid email format should fail
        client.post("http://localhost/request-body-validation/string") {
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
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid email format, got $status")
        }

        // Invalid description (too long) should fail
        client.post("http://localhost/request-body-validation/string") {
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
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for description length > 50, got $status")
        }
    }

    @Test
    fun `should validate number fields in RequestBody at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Valid request should succeed
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 25,
                    "rating": 8,
                    "percentage": 85.5,
                    "weight": 0.75
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Number validation successful"))
        }

        // Invalid age (negative) should fail
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": -5,
                    "rating": 8,
                    "percentage": 85.5,
                    "weight": 0.75
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for negative age, got $status")
        }

        // Invalid age (too high) should fail
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 150,
                    "rating": 8,
                    "percentage": 85.5,
                    "weight": 0.75
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for age > 120, got $status")
        }

        // Invalid rating (out of range) should fail
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 25,
                    "rating": 15,
                    "percentage": 85.5,
                    "weight": 0.75
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for rating > 10, got $status")
        }

        // Invalid percentage (too high) should fail
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 25,
                    "rating": 8,
                    "percentage": 150.0,
                    "weight": 0.75
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for percentage > 100.0, got $status")
        }
    }

    @Test
    fun `should validate nested objects in RequestBody at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Valid request should succeed
        client.post("http://localhost/request-body-validation/nested") {
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
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Nested validation successful"))
        }

        // Invalid nested object (invalid ZIP) should fail
        client.post("http://localhost/request-body-validation/nested") {
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
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid ZIP format, got $status")
        }

        // Invalid nested object in array should fail
        client.post("http://localhost/request-body-validation/nested") {
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
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid street length in array, got $status")
        }
    }

    @Test
    fun `should validate optional fields in RequestBody correctly`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Valid request with all fields should succeed
        client.post("http://localhost/request-body-validation/optional") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "requiredName": "John",
                    "optionalNickname": "Johnny",
                    "optionalAge": 25,
                    "optionalSsn": "123-45-6789"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Optional validation successful"))
        }

        // Valid request with only required fields should succeed
        client.post("http://localhost/request-body-validation/optional") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "requiredName": "John"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Invalid optional field (too short nickname) should fail
        client.post("http://localhost/request-body-validation/optional") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "requiredName": "John",
                    "optionalNickname": "Jo"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for optional nickname length < 3, got $status")
        }

        // Invalid optional field (negative age) should fail
        client.post("http://localhost/request-body-validation/optional") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "requiredName": "John",
                    "optionalAge": -5
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for negative optional age, got $status")
        }

        // Invalid optional field (wrong SSN format) should fail
        client.post("http://localhost/request-body-validation/optional") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "requiredName": "John",
                    "optionalSsn": "123456789"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid SSN format, got $status")
        }
    }

    @Test
    fun `should validate phone numbers in RequestBody at runtime`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Valid international phone number should succeed
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "John Doe",
                    "phoneNumber": "+1234567890",
                    "email": "john@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = bodyAsText()
            assertTrue(response.contains("Phone validation successful"))
            assertTrue(response.contains("+1234567890"))
        }

        // Valid US phone format (123) 456-7890 should succeed
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "Jane Smith",
                    "phoneNumber": "(123) 456-7890",
                    "email": "jane@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Valid US phone format 123-456-7890 should succeed
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "Bob Johnson",
                    "phoneNumber": "123-456-7890",
                    "email": "bob@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Valid US international format +1 123 456 7890 should succeed
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "Alice Cooper",
                    "phoneNumber": "+12345678901",
                    "email": "alice@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Invalid phone format (too short) should fail
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "John Doe",
                    "phoneNumber": "123",
                    "email": "john@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid phone format (too short), got $status")
        }

        // Invalid phone format (letters) should fail
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "John Doe",
                    "phoneNumber": "123-ABC-7890",
                    "email": "john@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid phone format (letters), got $status")
        }

        // Invalid phone format (wrong structure) should fail
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "John Doe",
                    "phoneNumber": "++1234567890",
                    "email": "john@example.com"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid phone format (double plus), got $status")
        }

        // Invalid email format should fail
        client.post("http://localhost/request-body-validation/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "name": "John Doe",
                    "phoneNumber": "+1234567890",
                    "email": "invalid-email"
                }
                """.trimIndent()
            )
        }.apply {
            assertTrue(status.value >= 400, "Should return error for invalid email format, got $status")
        }
    }

    @Test
    fun `should handle edge cases in RequestBody validation`() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                requestBodyValidationRoutes()
            }
        }

        // Test boundary values for string validation
        client.post("http://localhost/request-body-validation/string") {
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
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status, "Boundary values should be accepted")
        }

        // Test boundary values for number validation
        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 0,
                    "rating": 1,
                    "percentage": 0.0,
                    "weight": 0.0
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status, "Minimum boundary values should be accepted")
        }

        client.post("http://localhost/request-body-validation/number") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "age": 120,
                    "rating": 10,
                    "percentage": 100.0,
                    "weight": 1.0
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status, "Maximum boundary values should be accepted")
        }
    }
}
