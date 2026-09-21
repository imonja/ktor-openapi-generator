package com.papsign.ktor.openapigen.parameters.validation

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
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.server.application.Application

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

fun Application.configureParameterValidation() {
    setupBaseTestServer()
    apiRouting {
        validationTestRoutes()
    }
}
