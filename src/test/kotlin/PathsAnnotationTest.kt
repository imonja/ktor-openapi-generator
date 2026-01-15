import com.papsign.ktor.openapigen.annotations.Paths
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.routes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PathsAnnotationTest {

    @Paths("/api/v1/users/{id}", "/api/v2/users/{id}", "/users/{id}")
    data class UserParams(@PathParam("User ID") val id: String)

    // For routes without parameters, we use Unit directly
    // @Paths annotation doesn't work with Unit, so we'll test differently

    data class UserResponse(val message: String)
    data class ProductResponse(val message: String)

    @Test
    fun testPathsAnnotationWithParams() {
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    get<UserParams, UserResponse> { params ->
                        respond(UserResponse("User ${params.id}"))
                    }
                }
            }

            // Test all three alias paths work with parameters
            val response1 = client.get("/api/v1/users/123")
            assertEquals(HttpStatusCode.OK, response1.status)
            val body1 = response1.bodyAsText()
            assertEquals("""{"message":"User 123"}""", body1)

            val response2 = client.get("/api/v2/users/456")
            assertEquals(HttpStatusCode.OK, response2.status)
            val body2 = response2.bodyAsText()
            assertEquals("""{"message":"User 456"}""", body2)

            val response3 = client.get("/users/789")
            assertEquals(HttpStatusCode.OK, response3.status)
            val body3 = response3.bodyAsText()
            assertEquals("""{"message":"User 789"}""", body3)
        }
    }

    @Test
    fun testPathsAnnotationVsFunctionalRoutes() {
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    // @Paths annotation approach
                    get<UserParams, UserResponse> { params ->
                        respond(UserResponse("Annotation: User ${params.id}"))
                    }

                    // Functional routes() approach for comparison
                    routes("/api/v1/products", "/api/v2/products", "/products") {
                        get<Unit, ProductResponse> {
                            respond(ProductResponse("Functional: Product list"))
                        }
                    }
                }
            }

            // Test @Paths annotation
            val response1 = client.get("/api/v1/users/123")
            assertEquals(HttpStatusCode.OK, response1.status)
            assertEquals("""{"message":"Annotation: User 123"}""", response1.bodyAsText())

            // Test functional routes() approach
            val response2 = client.get("/api/v1/products")
            assertEquals(HttpStatusCode.OK, response2.status)
            assertEquals("""{"message":"Functional: Product list"}""", response2.bodyAsText())

            val response3 = client.get("/products")
            assertEquals(HttpStatusCode.OK, response3.status)
            assertEquals("""{"message":"Functional: Product list"}""", response3.bodyAsText())
        }
    }

    @Test
    fun testMixedPathsAndRegularRoutes() {
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    // Using @Paths annotation
                    get<UserParams, UserResponse> { params ->
                        respond(UserResponse("User ${params.id} from annotation"))
                    }

                    // Using regular route function
                    route("/regular") {
                        get<Unit, UserResponse> {
                            respond(UserResponse("Regular route"))
                        }
                    }
                }
            }

            // Test @Paths annotation works
            val response1 = client.get("/api/v1/users/test")
            assertEquals(HttpStatusCode.OK, response1.status)
            assertEquals("""{"message":"User test from annotation"}""", response1.bodyAsText())

            // Test regular route still works
            val response2 = client.get("/regular")
            assertEquals(HttpStatusCode.OK, response2.status)
            assertEquals("""{"message":"Regular route"}""", response2.bodyAsText())
        }
    }
}
