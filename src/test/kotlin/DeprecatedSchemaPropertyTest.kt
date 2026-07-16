import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.properties.description.Description
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
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
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

    data class Account(val id: String)

    data class ConversationResponse(
        @Deprecated("Use author instead")
        val account: Account,
        val author: Account
    )

    data class DescribedConversationResponse(
        @Description("Account that started the conversation")
        val account: Account
    )

    @Test
    fun `OpenAPI JSON marks Kotlin deprecated DTO properties as deprecated schema properties`(): Unit = testApplication {
        application {
            install(ServerContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                useSimpleSchemaNames()
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

    @Test
    fun `OpenAPI JSON wraps deprecated referenced DTO properties in allOf`(): Unit = testApplication {
        application {
            install(ServerContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                useSimpleSchemaNames()
                info {
                    version = "1.0"
                    title = "Deprecated Referenced Schema Property Test API"
                }
            }

            apiRouting {
                route("conversations") {
                    post<Unit, ConversationResponse, Unit> { _, _ ->
                        respond(ConversationResponse(Account("account-id"), Account("author-id")))
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
        val conversationSchema = schemas.values
            .filterIsInstance<Map<String, Any>>()
            .firstOrNull { schema ->
                val properties = schema["properties"] as? Map<*, *>
                properties?.containsKey("account") == true && properties.containsKey("author")
            }

        assertNotNull(conversationSchema, "OpenAPI should contain a schema for ConversationResponse")

        val accountProperty = conversationSchema.mapAt("properties").mapAt("account")
        assertEquals(null, accountProperty["\$ref"], "account should not have a top-level ref")
        assertEquals("#/components/schemas/Account", accountProperty.listAt("allOf")[0].mapAt().getValue("\$ref"))
        assertEquals(true, accountProperty["deprecated"], "account should be marked deprecated")

        val authorProperty = conversationSchema.mapAt("properties").mapAt("author")
        assertEquals("#/components/schemas/Account", authorProperty["\$ref"], "author should keep a plain ref")
        assertEquals(null, authorProperty["allOf"], "author should not be wrapped in allOf")
        assertEquals(null, authorProperty["deprecated"], "author should not be marked deprecated")
    }

    @Test
    fun `OpenAPI JSON wraps described referenced DTO properties in allOf`(): Unit = testApplication {
        application {
            install(ServerContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                useSimpleSchemaNames()
                info {
                    version = "1.0"
                    title = "Described Referenced Schema Property Test API"
                }
            }

            apiRouting {
                route("described-conversations") {
                    post<Unit, DescribedConversationResponse, Unit> { _, _ ->
                        respond(DescribedConversationResponse(Account("account-id")))
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
        val conversationSchema = schemas.values
            .filterIsInstance<Map<String, Any>>()
            .firstOrNull { schema ->
                val properties = schema["properties"] as? Map<*, *>
                properties?.containsKey("account") == true
            }

        assertNotNull(conversationSchema, "OpenAPI should contain a schema for DescribedConversationResponse")

        val accountProperty = conversationSchema.mapAt("properties").mapAt("account")
        assertEquals(null, accountProperty["\$ref"], "account should not have a top-level ref")
        assertEquals("#/components/schemas/Account", accountProperty.listAt("allOf")[0].mapAt().getValue("\$ref"))
        assertEquals("Account that started the conversation", accountProperty["description"])
    }

    private fun Map<String, Any>.mapAt(key: String): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? Map<String, Any> ?: error("Expected '$key' to be an object")
    }

    private fun Any.mapAt(): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any> ?: error("Expected value to be an object")
    }

    private fun Map<String, Any>.listAt(key: String): List<Any> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? List<Any> ?: error("Expected '$key' to be an array")
    }

    private fun OpenAPIGen.Configuration.useSimpleSchemaNames() {
        replaceModule(
            DefaultSchemaNamer,
            object : SchemaNamer {
                override fun get(type: KType): String {
                    return type.jvmErasure.simpleName ?: type.toString()
                }
            }
        )
    }
}
