import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
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
import kotlin.test.assertTrue

class QueryParamDefaultValueOpenAPITest {

    data class TestQueryWithDefaults(
        @param:QueryParam("String parameter with null default")
        val stringParam: String? = null,

        @param:QueryParam("Integer parameter with default value")
        val intParam: Int? = 42,

        @param:QueryParam("Boolean parameter with default value")
        val boolParam: Boolean? = true,

        @param:QueryParam("Long parameter with default value")
        val longParam: Long? = 0L,

        @param:QueryParam("Required non-nullable parameter")
        val requiredParam: String
    )

    data class TestResponse(val received: TestQueryWithDefaults)

    @Test
    fun `test OpenAPI JSON contains default values for parameters`(): Unit = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                jackson()
            }

            install(OpenAPIGen) {
                info {
                    version = "1.0"
                    title = "Test API"
                }
            }

            apiRouting {
                get<TestQueryWithDefaults, TestResponse>(
                    info("Test endpoint for QueryParam defaults")
                ) { params ->
                    respond(TestResponse(params))
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }

        // Get OpenAPI JSON
        val openAPIResponse = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openAPIResponse.status)

        val openAPIJson = openAPIResponse.body<Map<String, Any>>()

        // Navigate to parameters in the OpenAPI structure
        @Suppress("UNCHECKED_CAST")
        val paths = openAPIJson["paths"] as? Map<String, Any>
        assertNotNull(paths, "OpenAPI should contain paths")

        @Suppress("UNCHECKED_CAST")
        val rootPath = paths["/"] as? Map<String, Any>
        assertNotNull(rootPath, "OpenAPI should contain root path")

        @Suppress("UNCHECKED_CAST")
        val getOperation = rootPath["get"] as? Map<String, Any>
        assertNotNull(getOperation, "OpenAPI should contain GET operation")

        @Suppress("UNCHECKED_CAST")
        val parameters = getOperation["parameters"] as? List<Map<String, Any>>
        assertNotNull(parameters, "OpenAPI should contain parameters")

        // Find parameters and check their default values
        val intParam = parameters.find { it["name"] == "intParam" }
        assertNotNull(intParam, "Should have intParam parameter")
        assertEquals(42, intParam["default"], "intParam should have default value 42")
        assertEquals(false, intParam["required"], "intParam should not be required")

        val boolParam = parameters.find { it["name"] == "boolParam" }
        assertNotNull(boolParam, "Should have boolParam parameter")
        assertEquals(true, boolParam["default"], "boolParam should have default value true")
        assertEquals(false, boolParam["required"], "boolParam should not be required")

        val longParam = parameters.find { it["name"] == "longParam" }
        assertNotNull(longParam, "Should have longParam parameter")
        assertEquals(0, longParam["default"], "longParam should have default value 0")
        assertEquals(false, longParam["required"], "longParam should not be required")

        val stringParam = parameters.find { it["name"] == "stringParam" }
        assertNotNull(stringParam, "Should have stringParam parameter")
        assertEquals(null, stringParam["default"], "stringParam should have default value null")
        assertEquals(false, stringParam["required"], "stringParam should not be required")

        val requiredParam = parameters.find { it["name"] == "requiredParam" }
        assertNotNull(requiredParam, "Should have requiredParam parameter")
        assertEquals(true, requiredParam["required"], "requiredParam should be required")
        assertTrue(!requiredParam.containsKey("default"), "requiredParam should not have default value")
    }
}
