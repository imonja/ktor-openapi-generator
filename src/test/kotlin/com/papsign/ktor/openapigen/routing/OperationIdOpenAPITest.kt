package com.papsign.ktor.openapigen.routing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.papsign.ktor.openapigen.annotations.Paths
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.OperationId
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.operationId
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.route
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.routes
import com.papsign.ktor.openapigen.spec.OpenAPISpecGenerator
import com.papsign.ktor.openapigen.support.installJackson
import com.papsign.ktor.openapigen.support.installOpenAPI
import io.ktor.http.HttpMethod
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OperationIdOpenAPITest {
    private val mapper = jacksonObjectMapper()

    data class PostParams(
        @PathParam("User ID") val userId: String,
        @PathParam("Post ID") val postId: String
    )

    @Paths("/v1/users/{userId}", "/v2/users/{userId}")
    data class UserAliases(@PathParam("User ID") val userId: String)

    @Test
    fun `operation ID is generated from method and path`() {
        val document = generate {
            route("/users") {
                get<Unit, String> { respond("users") }
            }
        }

        assertEquals("getUsers", document.path("paths").path("/users").path("get").path("operationId").asText())
    }

    @ParameterizedTest
    @CsvSource(
        "POST, /users, postUsers",
        "GET, /v1/users, getV1Users",
        "GET, /, getRoot",
        "GET, /user-profile, getUserProfile",
        "GET, /user_profile, getUserProfile",
        "GET, /user.profile, getUserProfile"
    )
    fun `automatic IDs use camel case for the method and path`(method: String, path: String, expected: String) {
        val document = generate {
            route(path) {
                route<Unit, String>(HttpMethod(method), emptyArray()) { respond("users") }
            }
        }

        assertEquals(expected, document.path("paths").path(path).path(method.lowercase()).path("operationId").asText())
    }

    @Test
    fun `nested paths preserve parameter names with By prefixes`() {
        val document = generate {
            route("/users/{userId}") {
                route("posts/{postId}") {
                    get<PostParams, String> { respond(it.postId) }
                }
            }
        }

        assertEquals(
            "getUsersByUserIdPostsByPostId",
            document.path("paths").path("/users/{userId}/posts/{postId}").path("get").path("operationId").asText()
        )
    }

    @Test
    fun `explicit operation ID overrides the generated ID`() {
        val document = generate {
            route("/users") {
                get<Unit, String>(operationId("listUsers")) { respond("users") }
            }
        }

        assertEquals("listUsers", document.path("paths").path("/users").path("get").path("operationId").asText())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `blank explicit operation IDs fail with an explanation`(id: String) {
        val error = assertFailsWith<IllegalArgumentException> {
            generate {
                route("/users") {
                    get<Unit, String>(operationId(id)) { respond("users") }
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("operationId must not be blank"), error.message)
    }

    @ParameterizedTest
    @CsvSource(
        "/user-profile, , /user_profile, , getUserProfile",
        "/users, listUsers, /posts, listUsers, listUsers",
        "/users, , /posts, getUsers, getUsers",
        "/posts, getUsers, /users, , getUsers"
    )
    fun `duplicate IDs fail with the ID and both operations`(firstPath: String, firstId: String?, secondPath: String, secondId: String?, duplicateId: String) {
        val error = assertFailsWith<IllegalStateException> {
            generate {
                route(firstPath) {
                    get<Unit, String>(*firstId?.let { arrayOf(operationId(it)) }.orEmpty()) { respond("first") }
                }
                route(secondPath) {
                    get<Unit, String>(*secondId?.let { arrayOf(operationId(it)) }.orEmpty()) { respond("second") }
                }
            }
        }

        assertTrue(error.message.orEmpty().contains(duplicateId), error.message)
        assertTrue(error.message.orEmpty().contains("GET $firstPath"), error.message)
        assertTrue(error.message.orEmpty().contains("GET $secondPath"), error.message)
    }

    @Test
    fun `an explicit ID resolves an automatic collision without affecting another route`() {
        val document = generate {
            route("/user-profile") {
                get<Unit, String>(OperationId("readProfile")) { respond("first") }
            }
            route("/user_profile") {
                get<Unit, String> { respond("second") }
            }
        }

        assertEquals("readProfile", document.path("paths").path("/user-profile").path("get").path("operationId").asText())
        assertEquals("getUserProfile", document.path("paths").path("/user_profile").path("get").path("operationId").asText())
    }

    @Test
    fun `IDs must also be unique across methods on the same path`() {
        val error = assertFailsWith<IllegalStateException> {
            generate {
                route("/users") {
                    get<Unit, String> { respond("users") }
                    post<Unit, String, Unit>(operationId("getUsers")) { _, _ -> respond("created") }
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("getUsers"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /users"), error.message)
        assertTrue(error.message.orEmpty().contains("POST /users"), error.message)
    }

    @Test
    fun `functional path aliases receive distinct automatic IDs`() {
        val document = generate {
            routes("/v1/users", "/v2/users") {
                get<Unit, String> { respond("users") }
            }
        }

        assertEquals("getV1Users", document.path("paths").path("/v1/users").path("get").path("operationId").asText())
        assertEquals("getV2Users", document.path("paths").path("/v2/users").path("get").path("operationId").asText())
    }

    @Test
    fun `annotated path aliases receive distinct automatic IDs`() {
        val document = generate {
            get<UserAliases, String> { respond(it.userId) }
        }

        assertEquals("getV1UsersByUserId", document.path("paths").path("/v1/users/{userId}").path("get").path("operationId").asText())
        assertEquals("getV2UsersByUserId", document.path("paths").path("/v2/users/{userId}").path("get").path("operationId").asText())
    }

    @Test
    fun `sharing an explicit ID across aliases fails`() {
        val error = assertFailsWith<IllegalStateException> {
            generate {
                get<UserAliases, String>(operationId("getUser")) { respond(it.userId) }
            }
        }

        assertTrue(error.message.orEmpty().contains("getUser"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /v1/users/{userId}"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /v2/users/{userId}"), error.message)
    }

    private fun generate(routes: NormalOpenAPIRoute.() -> Unit) = mapper.readTree(
        OpenAPISpecGenerator.generate({
            installOpenAPI()
            installJackson()
            apiRouting(routes)
        })
    )
}
