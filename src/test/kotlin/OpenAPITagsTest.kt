import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class OpenAPITagsTest {

    // Тестовые теги для группировки эндпоинтов
    enum class TestTags(override val description: String) : APITag {
        TagA("Test tag A description"),
        TagB("Test tag B description"),
        TagC("Test tag C description")
    }

    data class SimpleRequest(val message: String)
    data class SimpleResponse(val result: String)

    data class ItemParams(
        @PathParam("Item ID")
        val itemId: String
    )

    data class SearchParams(
        @QueryParam("Query text")
        val q: String?
    )

    @Test
    fun `Single tag on route generates tags in OpenAPI spec`() = testApplication {
        application {
            install(ServerContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                info {
                    title = "Test API"
                    version = "1.0"
                }
            }

            apiRouting {
                route("/items") {
                    tag(TestTags.TagA) {
                        get<Unit, SimpleResponse>(
                            info("Get items")
                        ) { _ ->
                            respond(SimpleResponse("items data"))
                        }

                        post<Unit, SimpleResponse, SimpleRequest>(
                            info("Create item")
                        ) { _, request ->
                            respond(SimpleResponse("Created: ${request.message}"))
                        }
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        val openAPIResponse = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openAPIResponse.status)
        val openAPIJson = openAPIResponse.bodyAsText()

        // Verify tags section exists
        assertTrue(openAPIJson.contains("\"tags\""), "OpenAPI should contain tags section")

        // Verify our tag is defined
        assertTrue(openAPIJson.contains("TagA"), "Should contain TagA")
        assertTrue(openAPIJson.contains("Test tag A description"), "Should contain TagA description")

        // Verify endpoints are tagged
        assertTrue(openAPIJson.contains("\"/items\""), "Should contain /items endpoint")
        assertTrue(openAPIJson.contains("\"tags\":[\"TagA\"]"), "Endpoints should have TagA tag")

        println("✅ Single tag test passed")
    }

    @Test
    fun `Multiple tags on different routes generate separate tags`() = testApplication {
        application {
            install(ServerContentNegotiation) { jackson() }
            install(OpenAPIGen) {
                info {
                    title = "Multi-Tag Test API"
                    version = "1.0"
                }
            }

            apiRouting {
                route("/route-a") {
                    tag(TestTags.TagA) {
                        get<Unit, SimpleResponse>(
                            info("Get A")
                        ) { _ -> respond(SimpleResponse("data A")) }
                    }
                }

                route("/route-b") {
                    tag(TestTags.TagB) {
                        get<Unit, SimpleResponse>(
                            info("Get B")
                        ) { _ -> respond(SimpleResponse("data B")) }
                    }
                }

                route("/route-c") {
                    tag(TestTags.TagC) {
                        get<Unit, SimpleResponse>(
                            info("Get C")
                        ) { _ -> respond(SimpleResponse("data C")) }
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        val openAPIResponse = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openAPIResponse.status)
        val openAPIJson = openAPIResponse.bodyAsText()

        println("=== Generated OpenAPI JSON ===")
        println(openAPIJson)

        // Verify all tags are present
        assertTrue(openAPIJson.contains("TagA"), "Should contain TagA")
        assertTrue(openAPIJson.contains("TagB"), "Should contain TagB")
        assertTrue(openAPIJson.contains("TagC"), "Should contain TagC")

        // Verify tag descriptions
        assertTrue(openAPIJson.contains("Test tag A description"), "Should contain TagA description")
        assertTrue(openAPIJson.contains("Test tag B description"), "Should contain TagB description")
        assertTrue(openAPIJson.contains("Test tag C description"), "Should contain TagC description")

        println("✅ Multiple tags test passed")
    }

    @Test
    fun `Route with path parameters and tags works correctly`() = testApplication {
        application {
            install(ServerContentNegotiation) { jackson() }
            install(OpenAPIGen) {
                info {
                    title = "Path Params with Tags API"
                    version = "1.0"
                }
            }

            apiRouting {
                route("/items") {
                    tag(TestTags.TagA) {
                        route("/{itemId}") {
                            get<ItemParams, SimpleResponse>(
                                info("Get item by ID")
                            ) { params ->
                                respond(SimpleResponse("Item: ${params.itemId}"))
                            }
                        }
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        val openAPIResponse = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openAPIResponse.status)
        val openAPIJson = openAPIResponse.bodyAsText()

        // Verify tag is applied to path parameter route
        assertTrue(openAPIJson.contains("TagA"), "Should contain TagA")
        assertTrue(openAPIJson.contains("\"/items/{itemId}\""), "Should contain parameterized path")
        assertTrue(openAPIJson.contains("\"tags\":[\"TagA\"]"), "Parameterized endpoint should have TagA tag")

        println("✅ Path parameters with tags test passed")
    }
}
