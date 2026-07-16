import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class DeprecatedSchemaPropertyTest {

    data class RenameRequest(
        @Deprecated("Use currentName")
        val oldName: String,
        val currentName: String
    )

    data class RenameResponse(val accepted: Boolean)

    @Test
    fun `OpenAPI JSON marks Kotlin deprecated DTO properties as deprecated schema properties`(): Unit = testApplication {
        application {
            install(ServerContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                info {
                    version = "1.0"
                    title = "Deprecated Schema Property Test API"
                }
            }

            apiRouting {
                route("renames") {
                    post<Unit, RenameResponse, RenameRequest> { _, _ ->
                        respond(RenameResponse(true))
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        val openAPIResponse = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openAPIResponse.status)

        val openAPIJson = openAPIResponse.body<Map<String, Any>>()
        val schemas = openAPIJson.mapAt("components").mapAt("schemas")
        val renameRequestSchema = schemas.values
            .filterIsInstance<Map<String, Any>>()
            .firstOrNull { schema ->
                val properties = schema["properties"] as? Map<*, *>
                properties?.containsKey("oldName") == true && properties.containsKey("currentName")
            }

        assertNotNull(renameRequestSchema, "OpenAPI should contain a schema for RenameRequest")

        val oldNameProperty = renameRequestSchema.mapAt("properties").mapAt("oldName")
        assertEquals(true, oldNameProperty["deprecated"], "oldName should be marked deprecated")
    }

    private fun Map<String, Any>.mapAt(key: String): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? Map<String, Any> ?: error("Expected '$key' to be an object")
    }
}
