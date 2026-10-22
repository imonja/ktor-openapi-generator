package com.papsign.ktor.openapigen.schema

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.util.Date
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Date-time examples must not depend on the machine generating the document, otherwise a committed
 * OpenAPI document differs between a developer machine and CI. The test JVM runs in a non-UTC zone
 * (see `user.timezone` in build.gradle.kts), so a zone-dependent example fails here on any host.
 */
class DateTimeExampleOpenAPITest {
    data class Event(
        val instant: Instant,
        val zoned: ZonedDateTime,
        val date: Date,
        val offsetTime: OffsetTime,
    )

    @Test
    fun `date-time examples do not depend on the default time zone or the current time`(): Unit =
        testApplication {
            application {
                install(ServerContentNegotiation) {
                    jackson {
                        registerModule(JavaTimeModule())
                        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    }
                }

                install(OpenAPIGen) {
                    replaceModule(
                        DefaultSchemaNamer,
                        object : SchemaNamer {
                            override fun get(
                                type: KType,
                            ): String = type.jvmErasure.simpleName ?: type.toString()
                        },
                    )
                    info {
                        version = "1.0"
                        title = "Date-Time Example Test API"
                    }
                }

                apiRouting {
                    route("events") {
                        post<Unit, Event, Unit> { _, _ ->
                            respond(Event(Instant.EPOCH, ZonedDateTime.now(), Date(), OffsetTime.now()))
                        }
                    }
                }
            }

            assertNotEquals(
                "UTC",
                java.util.TimeZone
                    .getDefault()
                    .id,
                "the test JVM must run in a non-UTC zone",
            )

            val client = createClient {
                install(ContentNegotiation) { jackson() }
            }

            val openAPIResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openAPIResponse.status)

            val properties = openAPIResponse
                .body<Map<String, Any>>()
                .mapAt("components")
                .mapAt("schemas")
                .mapAt("Event")
                .mapAt("properties")

            assertEquals(listOf("2025-04-01T12:30:00Z"), properties.mapAt("instant")["examples"])
            assertEquals(listOf("2025-04-01T12:30:00Z"), properties.mapAt("zoned")["examples"])
            assertEquals(listOf("2025-04-01T12:30:00.000+00:00"), properties.mapAt("date")["examples"])
            assertEquals(listOf("11:30Z"), properties.mapAt("offsetTime")["examples"])
        }

    private fun Map<String, Any>.mapAt(
        key: String,
    ): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? Map<String, Any> ?: error("Expected '$key' to be an object")
    }
}
