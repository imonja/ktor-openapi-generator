import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.routes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RouteAliasTest {

    data class TestResponse(val message: String)

    @Test
    fun testRouteAliases() {
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    routes("/api/v1/users", "/api/v2/users", "/users") {
                        get<Unit, TestResponse> {
                            respond(TestResponse("Hello from aliases!"))
                        }
                    }
                }
            }

            // Test all three alias routes work
            val response1 = client.get("/api/v1/users")
            assertEquals(HttpStatusCode.OK, response1.status)
            val body1 = response1.bodyAsText()
            assertEquals("""{"message":"Hello from aliases!"}""", body1)

            val response2 = client.get("/api/v2/users")
            assertEquals(HttpStatusCode.OK, response2.status)
            val body2 = response2.bodyAsText()
            assertEquals("""{"message":"Hello from aliases!"}""", body2)

            val response3 = client.get("/users")
            assertEquals(HttpStatusCode.OK, response3.status)
            val body3 = response3.bodyAsText()
            assertEquals("""{"message":"Hello from aliases!"}""", body3)
        }
    }

    @Test
    fun testSingleRouteStillWorks() {
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    routes("/single") {
                        get<Unit, TestResponse> {
                            respond(TestResponse("Single route"))
                        }
                    }
                }
            }

            val response = client.get("/single")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEquals("""{"message":"Single route"}""", body)
        }
    }
}
