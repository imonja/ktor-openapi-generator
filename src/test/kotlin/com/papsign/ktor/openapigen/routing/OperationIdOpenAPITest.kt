package com.papsign.ktor.openapigen.routing

import com.fasterxml.jackson.databind.JsonNode
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
    fun `an explicit ID shared by annotated aliases is suffixed after the first alias`() {
        val document = generate {
            get<UserAliases, String>(operationId("getUser")) { respond(it.userId) }
        }

        assertEquals("getUser", document.operationId("/v1/users/{userId}", "get"))
        assertEquals("getUser_1", document.operationId("/v2/users/{userId}", "get"))
    }

    @Test
    fun `an explicit ID shared by functional aliases is suffixed by alias position`() {
        val document = generate {
            routes("/v1/profile/sharing/public-profile", "/v1/profile/create-share-link", "/v1/profile/share") {
                post<Unit, String, String>(OperationId("shareProfile")) { _, _ -> respond("shared") }
            }
        }

        assertEquals("shareProfile", document.operationId("/v1/profile/sharing/public-profile", "post"))
        assertEquals("shareProfile_1", document.operationId("/v1/profile/create-share-link", "post"))
        assertEquals("shareProfile_2", document.operationId("/v1/profile/share", "post"))
    }

    @Test
    fun `colliding automatic IDs within an alias group are suffixed`() {
        val document = generate {
            routes("/user-profile", "/user_profile") {
                get<Unit, String> { respond("profile") }
            }
        }

        assertEquals("getUserProfile", document.operationId("/user-profile", "get"))
        assertEquals("getUserProfile_1", document.operationId("/user_profile", "get"))
    }

    @Test
    fun `the suffix is the alias position even when earlier aliases have no suffix`() {
        val document = generate {
            routes("/user-profile", "/profile", "/user_profile") {
                get<Unit, String> { respond("profile") }
            }
        }

        assertEquals("getUserProfile", document.operationId("/user-profile", "get"))
        assertEquals("getProfile", document.operationId("/profile", "get"))
        assertEquals("getUserProfile_2", document.operationId("/user_profile", "get"))
    }

    @Test
    fun `nested alias groups are numbered as one flat group`() {
        val document = generate {
            routes("/v1", "/v2") {
                routes("/a", "/b") {
                    get<Unit, String>(operationId("x")) { respond("x") }
                }
            }
        }

        assertEquals("x", document.operationId("/v1/a", "get"))
        assertEquals("x_1", document.operationId("/v1/b", "get"))
        assertEquals("x_2", document.operationId("/v2/a", "get"))
        assertEquals("x_3", document.operationId("/v2/b", "get"))
    }

    @Test
    fun `annotated aliases inside functional aliases are numbered as one flat group`() {
        val document = generate {
            routes("/api", "/public") {
                get<UserAliases, String>(operationId("getUser")) { respond(it.userId) }
            }
        }

        assertEquals("getUser", document.operationId("/api/v1/users/{userId}", "get"))
        assertEquals("getUser_1", document.operationId("/api/v2/users/{userId}", "get"))
        assertEquals("getUser_2", document.operationId("/public/v1/users/{userId}", "get"))
        assertEquals("getUser_3", document.operationId("/public/v2/users/{userId}", "get"))
    }

    @Test
    fun `sharing an explicit ID across methods of the same alias still fails`() {
        val error = assertFailsWith<IllegalStateException> {
            generate {
                routes("/a", "/b") {
                    get<Unit, String>(operationId("x")) { respond("x") }
                    post<Unit, String, String>(operationId("x")) { _, _ -> respond("x") }
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("'x'"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /a"), error.message)
        assertTrue(error.message.orEmpty().contains("POST /a"), error.message)
    }

    @Test
    fun `sharing an explicit ID across separate alias groups still fails`() {
        val error = assertFailsWith<IllegalStateException> {
            generate {
                routes("/a", "/b") {
                    get<Unit, String>(operationId("x")) { respond("x") }
                }
                routes("/c", "/d") {
                    get<Unit, String>(operationId("x")) { respond("x") }
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("'x'"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /a"), error.message)
        assertTrue(error.message.orEmpty().contains("GET /c"), error.message)
    }

    private fun generate(routes: NormalOpenAPIRoute.() -> Unit) = mapper.readTree(
        OpenAPISpecGenerator.generate({
            installOpenAPI()
            installJackson()
            apiRouting(routes)
        })
    )

    private fun JsonNode.operationId(path: String, method: String) = path("paths").path(path).path(method).path("operationId").asText()
}
