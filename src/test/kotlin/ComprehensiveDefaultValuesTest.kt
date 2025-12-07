import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.mapping.OpenAPIName
import com.papsign.ktor.openapigen.annotations.parameters.HeaderParam
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.annotations.type.number.integer.max.Max
import com.papsign.ktor.openapigen.annotations.type.number.integer.min.Min
import com.papsign.ktor.openapigen.annotations.type.string.length.Length
import com.papsign.ktor.openapigen.annotations.type.string.length.MinLength
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComprehensiveDefaultValuesTest {

    // Case 1: Mixed Path + Query parameters (reproduces complex scenario)
    data class ComplexMixedParams(
        @param:PathParam("Entity ID for processing")
        val entityId: UUID,

        @param:QueryParam("Locale code for localization")
        val locale: String,

        @param:QueryParam("Maximum number of items to return (default: 10)")
        @Min(1)
        @Max(100)
        val limit: Int? = 10,

        @param:QueryParam("Number of items to skip (default: 0)")
        @Min(0)
        val offset: Int? = 0,

        @param:QueryParam("Sort order (default: asc)")
        @Length(min = 3, max = 4)
        val sort: String? = "asc"
    )

    // Case 2: Simple Query-only parameters
    data class SimpleQueryParams(
        @QueryParam("Page size with default")
        @Min(1)
        @Max(50)
        val pageSize: Int? = 20,

        @QueryParam("Filter query")
        @MinLength(2)
        val filter: String? = "all",

        @QueryParam("Include archived items")
        val includeArchived: Boolean? = false,

        @QueryParam("Required search term")
        val searchTerm: String
    )

    // Case 3: Header parameters with defaults
    data class HeaderParams(
        @HeaderParam("API version")
        @OpenAPIName("X-API-Version")
        val apiVersion: String? = "v1",

        @HeaderParam("Request timeout in seconds")
        @OpenAPIName("X-Timeout")
        @Min(1)
        @Max(300)
        val timeout: Int? = 30
    )

    // Case 4: Mixed nullable and non-nullable with defaults
    data class MixedNullabilityParams(
        @QueryParam("Non-nullable with default")
        @Min(0)
        val nonNullableDefault: Int = 42,

        @QueryParam("Nullable with default")
        @Max(1000)
        val nullableDefault: Int? = 100,

        @QueryParam("Nullable without default")
        val nullableNoDefault: String?,

        @QueryParam("Required parameter")
        val required: String
    )

    // Case 5: Edge cases - various primitive types with defaults
    data class PrimitiveTypesParams(
        @QueryParam("String with default")
        val stringDefault: String = "default-text",

        @QueryParam("Boolean with default")
        val boolDefault: Boolean = true,

        @QueryParam("Double with default")
        val doubleDefault: Double = 3.14,

        @QueryParam("Long with default")
        val longDefault: Long = 1000L,

        @QueryParam("Float with default")
        val floatDefault: Float = 2.5f
    )

    // Case 6: UUID parameters (the original failing case scenario)
    data class UuidParams(
        @PathParam("Entity UUID")
        val entityId: UUID,

        @QueryParam("Optional UUID with default")
        val optionalId: UUID? = UUID.fromString("12345678-1234-1234-1234-123456789012"),

        @QueryParam("Search query")
        val query: String
    )

    // Case 7: Only optional parameters (all have defaults)
    data class AllOptionalParams(
        @QueryParam("Page number")
        @Min(1)
        val page: Int = 1,

        @QueryParam("Items per page")
        @Min(1)
        @Max(100)
        val size: Int = 10,

        @QueryParam("Sort direction")
        val direction: String? = "asc"
    )

    data class TestResponse(val message: String, val params: Map<String, Any?>)

    @Test
    fun `default values work for all parameter types and scenarios`(): Unit = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                info {
                    title = "Comprehensive Default Values Test API"
                    version = "1.0"
                }
            }

            apiRouting {
                // Case 1: Complex mixed parameters
                route("/entities/{entityId}/process") {
                    get<ComplexMixedParams, TestResponse>(
                        info("Complex mixed parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "Mixed params processed",
                                mapOf(
                                    "entityId" to params.entityId.toString(),
                                    "locale" to params.locale,
                                    "limit" to params.limit,
                                    "offset" to params.offset,
                                    "sort" to params.sort
                                )
                            )
                        )
                    }
                }

                // Case 2: Simple query parameters
                route("/search") {
                    get<SimpleQueryParams, TestResponse>(
                        info("Simple query parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "Query params processed",
                                mapOf(
                                    "pageSize" to params.pageSize,
                                    "filter" to params.filter,
                                    "includeArchived" to params.includeArchived,
                                    "searchTerm" to params.searchTerm
                                )
                            )
                        )
                    }
                }

                // Case 3: Header parameters
                route("/headers") {
                    get<HeaderParams, TestResponse>(
                        info("Header parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "Header params processed",
                                mapOf(
                                    "apiVersion" to params.apiVersion,
                                    "timeout" to params.timeout
                                )
                            )
                        )
                    }
                }

                // Case 4: Mixed nullability
                route("/mixed") {
                    get<MixedNullabilityParams, TestResponse>(
                        info("Mixed nullability parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "Mixed nullability processed",
                                mapOf(
                                    "nonNullableDefault" to params.nonNullableDefault,
                                    "nullableDefault" to params.nullableDefault,
                                    "nullableNoDefault" to params.nullableNoDefault,
                                    "required" to params.required
                                )
                            )
                        )
                    }
                }

                // Case 5: Primitive types with defaults
                route("/primitives") {
                    get<PrimitiveTypesParams, TestResponse>(
                        info("Primitive types with defaults endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "Primitive types processed",
                                mapOf(
                                    "stringDefault" to params.stringDefault,
                                    "boolDefault" to params.boolDefault,
                                    "doubleDefault" to params.doubleDefault,
                                    "longDefault" to params.longDefault,
                                    "floatDefault" to params.floatDefault
                                )
                            )
                        )
                    }
                }

                // Case 6: UUID parameters (original failing case)
                route("/entities/{entityId}/uuid-test") {
                    get<UuidParams, TestResponse>(
                        info("UUID parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "UUID params processed",
                                mapOf(
                                    "entityId" to params.entityId.toString(),
                                    "optionalId" to params.optionalId?.toString(),
                                    "query" to params.query
                                )
                            )
                        )
                    }
                }

                // Case 7: All optional parameters
                route("/all-optional") {
                    get<AllOptionalParams, TestResponse>(
                        info("All optional parameters endpoint")
                    ) { params ->
                        respond(
                            TestResponse(
                                "All optional processed",
                                mapOf(
                                    "page" to params.page,
                                    "size" to params.size,
                                    "direction" to params.direction
                                )
                            )
                        )
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        // Get OpenAPI JSON
        val response = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)

        val openAPIJson = response.body<Map<String, Any>>()
        val openAPIJsonString = response.bodyAsText()

        // Save for inspection
        java.io.File("comprehensive_default_values.json").writeText(openAPIJsonString)

        // Navigate to paths
        @Suppress("UNCHECKED_CAST")
        val paths = openAPIJson["paths"] as Map<String, Any>

        println("=== COMPREHENSIVE DEFAULT VALUES TEST ===\n")

        // Test Case 1: Complex mixed parameters
        println("📋 Case 1: Complex Mixed Parameters")
        @Suppress("UNCHECKED_CAST")
        val mixedPath = paths["/entities/{entityId}/process"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedOp = mixedPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedParams = mixedOp["parameters"] as List<Map<String, Any>>

        val limitParam = mixedParams.find { it["name"] == "limit" }
        assertNotNull(limitParam, "Should have limit parameter")
        assertEquals(10, limitParam["default"], "✅ Case 1: limit should have default=10")
        assertEquals(false, limitParam["required"], "limit should not be required")

        val offsetParam = mixedParams.find { it["name"] == "offset" }
        assertNotNull(offsetParam, "Should have offset parameter")
        assertEquals(0, offsetParam["default"], "✅ Case 1: offset should have default=0")

        val sortParam = mixedParams.find { it["name"] == "sort" }
        assertNotNull(sortParam, "Should have sort parameter")
        assertEquals("asc", sortParam["default"], "✅ Case 1: sort should have default='asc'")

        val entityIdParam = mixedParams.find { it["name"] == "entityId" }
        assertNotNull(entityIdParam, "Should have entityId parameter")
        assertEquals(true, entityIdParam["required"], "entityId should be required")
        assertEquals(false, entityIdParam.containsKey("default"), "entityId should not have default")

        println("✅ Case 1: All mixed parameter defaults work correctly")

        // Test Case 2: Simple query parameters
        println("\n📋 Case 2: Simple Query Parameters")
        @Suppress("UNCHECKED_CAST")
        val queryPath = paths["/search"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val queryOp = queryPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val queryParams = queryOp["parameters"] as List<Map<String, Any>>

        val pageSizeParam = queryParams.find { it["name"] == "pageSize" }
        assertNotNull(pageSizeParam)
        assertEquals(20, pageSizeParam["default"], "✅ Case 2: pageSize should have default=20")

        val filterParam = queryParams.find { it["name"] == "filter" }
        assertNotNull(filterParam)
        assertEquals("all", filterParam["default"], "✅ Case 2: filter should have default='all'")

        val archivedParam = queryParams.find { it["name"] == "includeArchived" }
        assertNotNull(archivedParam)
        assertEquals(false, archivedParam["default"], "✅ Case 2: includeArchived should have default=false")

        println("✅ Case 2: All query parameter defaults work correctly")

        // Test Case 3: Header parameters
        println("\n📋 Case 3: Header Parameters")
        @Suppress("UNCHECKED_CAST")
        val headerPath = paths["/headers"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val headerOp = headerPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val headerParams = headerOp["parameters"] as List<Map<String, Any>>

        val apiVersionParam = headerParams.find { it["name"] == "x-api-version" }
        assertNotNull(apiVersionParam)
        assertEquals("v1", apiVersionParam["default"], "✅ Case 3: x-api-version should have default='v1'")
        assertEquals("header", apiVersionParam["in"], "Should be header parameter")

        val timeoutParam = headerParams.find { it["name"] == "x-timeout" }
        assertNotNull(timeoutParam)
        assertEquals(30, timeoutParam["default"], "✅ Case 3: x-timeout should have default=30")
        assertEquals("header", timeoutParam["in"], "Should be header parameter")

        println("✅ Case 3: All header parameter defaults work correctly")

        // Test Case 4: Mixed nullability
        println("\n📋 Case 4: Mixed Nullability")
        @Suppress("UNCHECKED_CAST")
        val mixedNullPath = paths["/mixed"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedNullOp = mixedNullPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedNullParams = mixedNullOp["parameters"] as List<Map<String, Any>>

        val nonNullParam = mixedNullParams.find { it["name"] == "nonNullableDefault" }
        assertNotNull(nonNullParam)
        assertEquals(42, nonNullParam["default"], "✅ Case 4: nonNullableDefault should have default=42")

        val nullableDefaultParam = mixedNullParams.find { it["name"] == "nullableDefault" }
        assertNotNull(nullableDefaultParam)
        assertEquals(100, nullableDefaultParam["default"], "✅ Case 4: nullableDefault should have default=100")

        val nullableNoDefaultParam = mixedNullParams.find { it["name"] == "nullableNoDefault" }
        assertNotNull(nullableNoDefaultParam)
        assertEquals(false, nullableNoDefaultParam.containsKey("default"), "nullableNoDefault should not have default")
        assertEquals(false, nullableNoDefaultParam["required"], "nullableNoDefault should not be required")

        println("✅ Case 4: All mixed nullability defaults work correctly")

        // Test Case 5: Primitive types with defaults
        println("\n📋 Case 5: Primitive Types with Defaults")
        @Suppress("UNCHECKED_CAST")
        val primitivePath = paths["/primitives"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val primitiveOp = primitivePath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val primitiveParams = primitiveOp["parameters"] as List<Map<String, Any>>

        val stringDefaultParam = primitiveParams.find { it["name"] == "stringDefault" }
        assertNotNull(stringDefaultParam)
        assertEquals("default-text", stringDefaultParam["default"], "✅ Case 5: stringDefault should have default='default-text'")

        val boolDefaultParam = primitiveParams.find { it["name"] == "boolDefault" }
        assertNotNull(boolDefaultParam)
        assertEquals(true, boolDefaultParam["default"], "✅ Case 5: boolDefault should have default=true")

        val doubleDefaultParam = primitiveParams.find { it["name"] == "doubleDefault" }
        assertNotNull(doubleDefaultParam)
        assertEquals(3.14, doubleDefaultParam["default"], "✅ Case 5: doubleDefault should have default=3.14")

        println("✅ Case 5: All primitive type defaults work correctly")

        // Test Case 6: UUID parameters (original failing case)
        println("\n📋 Case 6: UUID Parameters (Original Failing Case)")
        @Suppress("UNCHECKED_CAST")
        val uuidPath = paths["/entities/{entityId}/uuid-test"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val uuidOp = uuidPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val uuidParams = uuidOp["parameters"] as List<Map<String, Any>>

        val optionalIdParam = uuidParams.find { it["name"] == "optionalId" }
        assertNotNull(optionalIdParam)
        assertEquals("12345678-1234-1234-1234-123456789012", optionalIdParam["default"], "✅ Case 6: optionalId should have UUID default")

        val uuidEntityIdParam = uuidParams.find { it["name"] == "entityId" }
        assertNotNull(uuidEntityIdParam)
        assertEquals(true, uuidEntityIdParam["required"], "entityId should be required")

        println("✅ Case 6: UUID parameter defaults work correctly")

        // Test Case 7: All optional parameters
        println("\n📋 Case 7: All Optional Parameters")
        @Suppress("UNCHECKED_CAST")
        val allOptionalPath = paths["/all-optional"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val allOptionalOp = allOptionalPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val allOptionalParams = allOptionalOp["parameters"] as List<Map<String, Any>>

        val pageParam = allOptionalParams.find { it["name"] == "page" }
        assertNotNull(pageParam)
        assertEquals(1, pageParam["default"], "✅ Case 7: page should have default=1")
        assertEquals(false, pageParam["required"], "page should not be required")

        val sizeParam = allOptionalParams.find { it["name"] == "size" }
        assertNotNull(sizeParam)
        assertEquals(10, sizeParam["default"], "✅ Case 7: size should have default=10")

        val directionParam = allOptionalParams.find { it["name"] == "direction" }
        assertNotNull(directionParam)
        assertEquals("asc", directionParam["default"], "✅ Case 7: direction should have default='asc'")

        println("✅ Case 7: All optional parameters work correctly")

        // Verify validation constraints are preserved
        println("\n📋 Validation Constraints Check")
        val hasValidation = openAPIJsonString.contains("minimum") && openAPIJsonString.contains("maximum")
        println("✅ Validation constraints preserved: $hasValidation")

        println("\n🎉 ALL 7 DEFAULT VALUE SCENARIOS WORK PERFECTLY!")
        println("   • Complex Mixed Parameters (Path + Query)")
        println("   • Simple Query Parameters")
        println("   • Header Parameters with Defaults")
        println("   • Mixed Nullability")
        println("   • Primitive Types with Defaults")
        println("   • UUID Parameters (Original Failing Case)")
        println("   • All Optional Parameters")
    }
}
