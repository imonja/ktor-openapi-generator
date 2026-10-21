package com.papsign.ktor.openapigen.swaggerui

import com.papsign.ktor.openapigen.SwaggerUIVersion
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SwaggerUIVersionTest {

    private fun versionOf(resource: String): String? = SwaggerUIVersion::class.java.getResourceAsStream(resource)?.use { stream ->
        Properties().apply { load(stream) }.getProperty("version")
    }

    @Test
    fun `version comes from the WebJar metadata`() {
        val webJarVersion = assertNotNull(versionOf("/META-INF/maven/org.webjars/swagger-ui/pom.properties"))

        assertEquals(webJarVersion, SwaggerUIVersion.version)
    }

    @Test
    fun `generated fallback matches the WebJar version`() {
        val webJarVersion = assertNotNull(versionOf("/META-INF/maven/org.webjars/swagger-ui/pom.properties"))
        val fallbackVersion = assertNotNull(versionOf("/com/papsign/ktor/openapigen/swagger-ui.properties"))

        assertEquals(webJarVersion, fallbackVersion)
    }
}
