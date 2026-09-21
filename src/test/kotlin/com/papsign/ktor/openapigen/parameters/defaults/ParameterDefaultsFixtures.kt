package com.papsign.ktor.openapigen.parameters.defaults

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
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import java.util.*

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

fun Application.configureParameterDefaults() {
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
