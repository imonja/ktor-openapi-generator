package com.papsign.ktor.openapigen.parameters.defaults

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParameterDefaultsOpenAPITest {

    @Test
    fun `documents defaults alongside required path parameters`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val mixedPath = paths["/entities/{entityId}/process"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedOp = mixedPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedParams = mixedOp["parameters"] as List<Map<String, Any>>

        val limitParam = mixedParams.find { it["name"] == "limit" }
        assertNotNull(limitParam, "Should have limit parameter")
        assertEquals(10, limitParam["default"], "limit should have default=10")
        assertEquals(false, limitParam["required"], "limit should not be required")

        val offsetParam = mixedParams.find { it["name"] == "offset" }
        assertNotNull(offsetParam, "Should have offset parameter")
        assertEquals(0, offsetParam["default"], "offset should have default=0")

        val sortParam = mixedParams.find { it["name"] == "sort" }
        assertNotNull(sortParam, "Should have sort parameter")
        assertEquals("asc", sortParam["default"], "sort should have default='asc'")

        val entityIdParam = mixedParams.find { it["name"] == "entityId" }
        assertNotNull(entityIdParam, "Should have entityId parameter")
        assertEquals(true, entityIdParam["required"], "entityId should be required")
        assertEquals(false, entityIdParam.containsKey("default"), "entityId should not have default")
    }

    @Test
    fun `documents query parameter defaults`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val queryPath = paths["/search"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val queryOp = queryPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val queryParams = queryOp["parameters"] as List<Map<String, Any>>

        val pageSizeParam = queryParams.find { it["name"] == "pageSize" }
        assertNotNull(pageSizeParam)
        assertEquals(20, pageSizeParam["default"], "pageSize should have default=20")

        val filterParam = queryParams.find { it["name"] == "filter" }
        assertNotNull(filterParam)
        assertEquals("all", filterParam["default"], "filter should have default='all'")

        val archivedParam = queryParams.find { it["name"] == "includeArchived" }
        assertNotNull(archivedParam)
        assertEquals(false, archivedParam["default"], "includeArchived should have default=false")
    }

    @Test
    fun `documents header parameter defaults`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val headerPath = paths["/headers"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val headerOp = headerPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val headerParams = headerOp["parameters"] as List<Map<String, Any>>

        val apiVersionParam = headerParams.find { it["name"] == "x-api-version" }
        assertNotNull(apiVersionParam)
        assertEquals("v1", apiVersionParam["default"], "x-api-version should have default='v1'")
        assertEquals("header", apiVersionParam["in"], "Should be header parameter")

        val timeoutParam = headerParams.find { it["name"] == "x-timeout" }
        assertNotNull(timeoutParam)
        assertEquals(30, timeoutParam["default"], "x-timeout should have default=30")
        assertEquals("header", timeoutParam["in"], "Should be header parameter")
    }

    @Test
    fun `documents defaults with mixed nullability`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val mixedNullPath = paths["/mixed"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedNullOp = mixedNullPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val mixedNullParams = mixedNullOp["parameters"] as List<Map<String, Any>>

        val nonNullParam = mixedNullParams.find { it["name"] == "nonNullableDefault" }
        assertNotNull(nonNullParam)
        assertEquals(42, nonNullParam["default"], "nonNullableDefault should have default=42")

        val nullableDefaultParam = mixedNullParams.find { it["name"] == "nullableDefault" }
        assertNotNull(nullableDefaultParam)
        assertEquals(100, nullableDefaultParam["default"], "nullableDefault should have default=100")

        val nullableNoDefaultParam = mixedNullParams.find { it["name"] == "nullableNoDefault" }
        assertNotNull(nullableNoDefaultParam)
        assertEquals(false, nullableNoDefaultParam.containsKey("default"), "nullableNoDefault should not have default")
        assertEquals(false, nullableNoDefaultParam["required"], "nullableNoDefault should not be required")
    }

    @Test
    fun `documents primitive parameter defaults`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val primitivePath = paths["/primitives"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val primitiveOp = primitivePath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val primitiveParams = primitiveOp["parameters"] as List<Map<String, Any>>

        val stringDefaultParam = primitiveParams.find { it["name"] == "stringDefault" }
        assertNotNull(stringDefaultParam)
        assertEquals("default-text", stringDefaultParam["default"], "stringDefault should have default='default-text'")

        val boolDefaultParam = primitiveParams.find { it["name"] == "boolDefault" }
        assertNotNull(boolDefaultParam)
        assertEquals(true, boolDefaultParam["default"], "boolDefault should have default=true")

        val doubleDefaultParam = primitiveParams.find { it["name"] == "doubleDefault" }
        assertNotNull(doubleDefaultParam)
        assertEquals(3.14, doubleDefaultParam["default"], "doubleDefault should have default=3.14")
    }

    @Test
    fun `documents UUID parameter defaults`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val uuidPath = paths["/entities/{entityId}/uuid-test"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val uuidOp = uuidPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val uuidParams = uuidOp["parameters"] as List<Map<String, Any>>

        val optionalIdParam = uuidParams.find { it["name"] == "optionalId" }
        assertNotNull(optionalIdParam)
        assertEquals("12345678-1234-1234-1234-123456789012", optionalIdParam["default"], "optionalId should have UUID default")

        val uuidEntityIdParam = uuidParams.find { it["name"] == "entityId" }
        assertNotNull(uuidEntityIdParam)
        assertEquals(true, uuidEntityIdParam["required"], "entityId should be required")
    }

    @Test
    fun `documents an all-optional parameter group`() = testApplication {
        application { configureParameterDefaults() }
        val paths = parameterPaths()

        @Suppress("UNCHECKED_CAST")
        val allOptionalPath = paths["/all-optional"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val allOptionalOp = allOptionalPath["get"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val allOptionalParams = allOptionalOp["parameters"] as List<Map<String, Any>>

        val pageParam = allOptionalParams.find { it["name"] == "page" }
        assertNotNull(pageParam)
        assertEquals(1, pageParam["default"], "page should have default=1")
        assertEquals(false, pageParam["required"], "page should not be required")

        val sizeParam = allOptionalParams.find { it["name"] == "size" }
        assertNotNull(sizeParam)
        assertEquals(10, sizeParam["default"], "size should have default=10")

        val directionParam = allOptionalParams.find { it["name"] == "direction" }
        assertNotNull(directionParam)
        assertEquals("asc", directionParam["default"], "direction should have default='asc'")
    }

    private suspend fun ApplicationTestBuilder.parameterPaths(): Map<String, Any> {
        val client = createClient {
            install(ContentNegotiation) { jackson() }
        }
        val response = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)
        val document = response.body<Map<String, Any>>()
        @Suppress("UNCHECKED_CAST")
        return document["paths"] as Map<String, Any>
    }
}
