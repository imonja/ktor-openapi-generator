package com.papsign.ktor.openapigen.support

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.type.common.ConstraintViolation
import com.papsign.ktor.openapigen.exceptions.OpenAPIRequiredFieldException
import com.papsign.ktor.openapigen.interop.withAPI
import com.papsign.ktor.openapigen.model.server.ServerModel
import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.reflect.KType

data class Error(val id: String, val msg: String)

class ProperException(msg: String, val id: String = "proper.exception") : Exception(msg)

fun Application.setupBaseTestServer() {
    // define basic OpenAPI info
    val api = install(OpenAPIGen) {
        info {
            version = "0.1"
            title = "Test API"
            description = "The Test API"
            contact {
                name = "Support"
                email = "support@test.com"
            }
        }
        server("https://api.test.com/") {
            description = "Main production server"
        }
        replaceModule(
            DefaultSchemaNamer,
            object : SchemaNamer {
                val regex = Regex("[A-Za-z0-9_.]+")
                override fun get(type: KType): String {
                    return type.toString().replace(regex) { it.value.split(".").last() }
                        .replace(Regex(">|<|, "), "_")
                }
            }
        )
    }

    install(ContentNegotiation) {
        jackson {
            enable(
                DeserializationFeature.WRAP_EXCEPTIONS,
                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
            )

            enable(SerializationFeature.WRAP_EXCEPTIONS, SerializationFeature.INDENT_OUTPUT)

            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)

            setSerializationInclusion(JsonInclude.Include.NON_NULL)

            setDefaultPrettyPrinter(
                DefaultPrettyPrinter().apply {
                    indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                    indentObjectsWith(DefaultIndenter("  ", "\n"))
                }
            )

            registerModule(JavaTimeModule())
        }
    }

    // StatusPage interop, can also define exceptions per-route
    install(StatusPages) {
        withAPI(api) {
            exception<JsonMappingException, Error>(HttpStatusCode.BadRequest) {
                it.printStackTrace()
                Error("mapping.json", it.localizedMessage)
            }
            exception<ConstraintViolation, Error>(HttpStatusCode.BadRequest) {
                Error("violation.constraint", it.localizedMessage)
            }
            exception<OpenAPIRequiredFieldException, Error>(HttpStatusCode.BadRequest) {
                Error("field.required", it.localizedMessage)
            }
            exception<ProperException, Error>(HttpStatusCode.BadRequest) {
                it.printStackTrace()
                Error(it.id, it.localizedMessage)
            }
        }
    }

    // serve OpenAPI and redirect from root
    routing {
        get("/openapi.json") {
            val host = ServerModel(
                call.request.origin.scheme + "://" + call.request.host() + if (setOf(
                        80,
                        443
                    ).contains(call.request.port())
                ) {
                    ""
                } else {
                    ":${call.request.port()}"
                }
            )
            this.call.application.openAPIGen.api.servers.add(0, host)
            call.respond(call.application.openAPIGen.api.serialize())
            call.application.openAPIGen.api.servers.remove(host)
        }

        get("/") {
            call.respondRedirect("/swagger-ui/index.html?url=/openapi.json", true)
        }
    }
}
