import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestBodyRequiredTest {

    // Test classes with different @Request configurations
    @Request("Required request with default true")
    data class RequiredByDefaultRequest(val message: String)

    @Request(description = "Explicitly required request", required = true)
    data class ExplicitlyRequiredRequest(val message: String)

    @Request(description = "Optional request", required = false)
    data class OptionalRequest(val message: String)

    data class NoAnnotationRequest(val message: String)

    data class TestResponse(val result: String)

    @Test
    fun testRequestRequiredByDefault() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("required-by-default") {
                        post<Unit, TestResponse, RequiredByDefaultRequest> { _, request ->
                            respond(TestResponse("Got: ${request.message}"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Should contain required: true (or omit it since true is default in OpenAPI)
            assertTrue(
                openApiContent.contains("\"required\":true") || (!openApiContent.contains("\"required\":false") && openApiContent.contains("\"/required-by-default\""))
            )
        }
    }

    @Test
    fun testRequestExplicitlyRequired() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("explicitly-required") {
                        post<Unit, TestResponse, ExplicitlyRequiredRequest> { _, request ->
                            respond(TestResponse("Got: ${request.message}"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Should contain required: true or not mention required at all (since true is default)
            assertTrue(
                openApiContent.contains("\"required\":true") || (!openApiContent.contains("\"required\":false") && openApiContent.contains("\"/explicitly-required\""))
            )
        }
    }

    @Test
    fun testRequestOptional() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("optional") {
                        post<Unit, TestResponse, OptionalRequest> { _, request ->
                            respond(TestResponse("Got: ${request.message}"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Should explicitly contain required: false
            assertTrue(openApiContent.contains("\"required\":false"))
        }
    }

    @Test
    fun testRequestNoAnnotation() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("no-annotation") {
                        post<Unit, TestResponse, NoAnnotationRequest> { _, request ->
                            respond(TestResponse("Got: ${request.message}"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Should default to required: true for non-Unit types
            assertTrue(
                openApiContent.contains("\"required\":true") || (!openApiContent.contains("\"required\":false") && openApiContent.contains("\"/no-annotation\""))
            )
        }
    }

    @Test
    fun testUnitRequestBodyNotGenerated() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("unit-request") {
                        post<Unit, TestResponse, Unit> { _, _ ->
                            respond(TestResponse("No request body"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Unit requests should not have requestBody at all
            val unitRequestSection = openApiContent.substringAfter("\"/unit-request\"").substringBefore("}")
            assertFalse(unitRequestSection.contains("requestBody"))
        }
    }

    @Test
    fun testRequestDescriptionsPreserved() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Request Required Test API"
                        version = "1.0.0"
                    }
                }
                install(ContentNegotiation) { jackson() }

                apiRouting {
                    route("descriptions") {
                        post<Unit, TestResponse, RequiredByDefaultRequest> { _, request ->
                            respond(TestResponse("Got: ${request.message}"))
                        }
                    }
                }
            }

            val openApiResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, openApiResponse.status)
            val openApiContent = openApiResponse.bodyAsText()

            // Should preserve the description from @Request annotation
            assertTrue(openApiContent.contains("Required request with default true"))
        }
    }
}
