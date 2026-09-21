package com.papsign.ktor.openapigen.content.validation

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
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.server.application.Application

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

fun Application.configureRequestBodyValidation() {
    setupBaseTestServer()
    apiRouting {
        requestBodyValidationRoutes()
    }
}
