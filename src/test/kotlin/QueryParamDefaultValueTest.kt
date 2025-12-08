import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.interop.withAPI
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
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QueryParamDefaultValueTest {

    data class TestQueryWithDefaults(
        @param:QueryParam("String parameter with null default")
        val stringParam: String? = null,

        @param:QueryParam("Integer parameter with default value")
        val intParam: Int? = 42,

        @param:QueryParam("Boolean parameter with default value")
        val boolParam: Boolean? = true,

        @param:QueryParam("Required non-nullable parameter")
        val requiredParam: String
    )

    data class TestResponse(val received: TestQueryWithDefaults)

    @Test
    fun `test QueryParam default values work correctly`(): Unit = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                jackson()
            }

            val api = install(OpenAPIGen) {
                info {
                    version = "1.0"
                    title = "Test API"
                }
            }

            install(StatusPages) {
                withAPI(api) {
                    exception<Exception, String>(HttpStatusCode.InternalServerError) {
                        it.message ?: "Unknown error"
                    }
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

        // Test 1: Only required parameter provided - should use defaults
        val response1 = client.get("/?requiredParam=test")
        assertEquals(HttpStatusCode.OK, response1.status)

        val result1 = response1.body<TestResponse>()
        assertEquals("test", result1.received.requiredParam)
        assertEquals(null, result1.received.stringParam)
        assertEquals(42, result1.received.intParam)
        assertEquals(true, result1.received.boolParam)

        // Test 2: Override some defaults
        val response2 = client.get("/?requiredParam=test&intParam=100&stringParam=custom")
        assertEquals(HttpStatusCode.OK, response2.status)

        val result2 = response2.body<TestResponse>()
        assertEquals("test", result2.received.requiredParam)
        assertEquals("custom", result2.received.stringParam)
        assertEquals(100, result2.received.intParam)
        assertEquals(true, result2.received.boolParam) // Still default

        // Test 3: Missing required parameter should fail
        val response3 = client.get("/")
        // This should fail but let's just check it's not 200
        assert(response3.status != HttpStatusCode.OK)
    }
}
