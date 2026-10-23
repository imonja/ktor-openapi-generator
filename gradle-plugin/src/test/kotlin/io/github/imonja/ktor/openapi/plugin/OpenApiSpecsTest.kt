package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.InvalidUserDataException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenApiSpecsTest {
    @Test
    fun `task names capitalize the spec name`() {
        assertEquals("generateMobileNativeAuthOpenApiSpec", OpenApiSpecs.taskName("generate", "mobileNativeAuth"))
        assertEquals("checkV2OpenApiSpec", OpenApiSpecs.taskName("check", "v2"))
    }

    @Test
    fun `spec names are letters and digits starting with a letter`() {
        OpenApiSpecs.validateName("mobileAuth2")
        listOf("mobile-auth", "2fa", "a b", "a:b", "").forEach { name ->
            assertFailsWith<InvalidUserDataException>(name) { OpenApiSpecs.validateName(name) }
        }
    }

    @Test
    fun `configuration error`() {
        assertNull(OpenApiSpecs.configurationError(moduleSet = true, specOutputs = emptyMap()))
        assertNull(OpenApiSpecs.configurationError(moduleSet = false, specOutputs = mapOf("a" to "a.json", "b" to "b.json")))
        assertEquals(
            "ktorOpenApi.module and ktorOpenApi.specs cannot be combined; move the module into specs { register(\"…\") { } }",
            OpenApiSpecs.configurationError(moduleSet = true, specOutputs = mapOf("a" to "a.json")),
        )
        assertEquals(
            "ktorOpenApi specs a and c write to the same file x.json; give each spec its own outputFile",
            OpenApiSpecs.configurationError(moduleSet = false, specOutputs = mapOf("a" to "x.json", "b" to "b.json", "c" to "x.json")),
        )
    }
}
