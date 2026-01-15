import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.Paths
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.routes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class RouteAliasOpenAPITest {

    @Paths("/api/v1/users/{id}", "/api/v2/users/{id}", "/users/{id}")
    data class UserParams(@PathParam("User ID") val id: String)

    data class UserResponse(val message: String)
    data class ProductResponse(val message: String)

    @Test
    fun testRouteAliasesInOpenAPI() {
        testApplication {
            application {
                install(OpenAPIGen) {
                    serveOpenApiJson = true
                    info {
                        title = "Route Aliases API"
                        version = "1.0.0"
                        description = "API demonstrating route aliases"
                    }
                }
                install(ContentNegotiation) {
                    jackson()
                }

                apiRouting {
                    // Functional aliases with routes()
                    routes("/api/v1/products", "/api/v2/products", "/products") {
                        get<Unit, ProductResponse> {
                            respond(ProductResponse("Product list"))
                        }
                    }

                    // Annotation aliases with @Paths
                    get<UserParams, UserResponse> { params ->
                        respond(UserResponse("User ${params.id}"))
                    }
                }
            }

            // Get OpenAPI JSON
            val response = client.get("/openapi.json")
            val openAPIJson = response.bodyAsText()

            // OpenAPI JSON generated with route aliases

            // Verify that all paths are present in OpenAPI spec
            assert(openAPIJson.contains("\"/api/v1/products\""))
            assert(openAPIJson.contains("\"/api/v2/products\""))
            assert(openAPIJson.contains("\"/products\""))
            assert(openAPIJson.contains("\"/api/v1/users/{id}\""))
            assert(openAPIJson.contains("\"/api/v2/users/{id}\""))
            assert(openAPIJson.contains("\"/users/{id}\""))
        }
    }
}
