package com.papsign.ktor.openapigen.spec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.delete
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

data class SpecItem(val name: String)

fun Application.specTestModule() {
    install(OpenAPIGen) {
        serveOpenApiJson = true
        serveSwaggerUi = false
        info {
            title = "Spec Test API"
            version = "1.0.0"
        }
    }
    install(ContentNegotiation) { jackson() }
    apiRouting {
        route("items") {
            get<Unit, List<SpecItem>> { respond(emptyList()) }
            post<Unit, SpecItem, SpecItem> { _, body -> respond(body) }
            put<Unit, SpecItem, SpecItem> { _, body -> respond(body) }
            delete<Unit, SpecItem> { respond(SpecItem("deleted")) }
        }
    }
}

fun Application.specModuleWithoutOpenApiGen() {
    install(ContentNegotiation) { jackson() }
}

fun Application.specModuleWithNonDaemonThread() {
    specTestModule()
    Thread { Thread.sleep(Long.MAX_VALUE) }.apply { isDaemon = false }.start()
}

class OpenAPISpecGeneratorTest {
    private val mapper = jacksonObjectMapper()
    private val module = "com.papsign.ktor.openapigen.spec.OpenAPISpecGeneratorTestKt"

    @Test
    fun `generated document equals the served document`() {
        var served = ""
        testApplication {
            application { specTestModule() }
            served = client.get("/openapi.json").bodyAsText()
        }

        val generated = OpenAPISpecGenerator.generate(Application::specTestModule)

        assertEquals(mapper.readTree(served), mapper.readTree(generated))
    }

    @Test
    fun `module can be referenced by fully qualified name`() {
        val byName = OpenAPISpecGenerator.generate("$module.specTestModule")

        assertEquals(mapper.readTree(OpenAPISpecGenerator.generate(Application::specTestModule)), mapper.readTree(byName))
    }

    @Test
    fun `operations follow declaration order`() {
        val document = mapper.readTree(OpenAPISpecGenerator.generate(Application::specTestModule))

        assertEquals(listOf("get", "post", "put", "delete"), document.path("paths").path("/items").fieldNames().asSequence().toList())
    }

    @Test
    fun `output is pretty printed and ends with a newline`() {
        val generated = OpenAPISpecGenerator.generate(Application::specTestModule)

        assertTrue(generated.startsWith("{\n  \""), generated.take(20))
        assertTrue(generated.contains("\"openapi\": \""))
        assertTrue(generated.endsWith("}\n"))
    }

    @Test
    fun `missing OpenAPIGen fails with explanation`() {
        val error = assertFailsWith<IllegalStateException> {
            OpenAPISpecGenerator.generate("$module.specModuleWithoutOpenApiGen")
        }

        assertTrue(error.message!!.contains("did not install OpenAPIGen"), error.message)
    }

    @Test
    fun `module failure propagates the original exception`() {
        val error = assertFailsWith<IllegalArgumentException> {
            OpenAPISpecGenerator.generate({ throw IllegalArgumentException("missing configuration") })
        }

        assertEquals("missing configuration", error.message)
    }

    @Test
    fun `failed generation keeps the existing file`(@TempDir dir: Path) {
        val output = dir.resolve("openapi.json")
        Files.writeString(output, "previous")

        assertFailsWith<IllegalStateException> {
            OpenAPISpecGenerator.generateTo(output, "$module.specModuleWithoutOpenApiGen")
        }

        assertEquals("previous", Files.readString(output))
        assertEquals(listOf("openapi.json"), Files.list(dir).use { files -> files.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `generateTo replaces the file and creates parent directories`(@TempDir dir: Path) {
        val output = dir.resolve("nested/openapi.json")

        OpenAPISpecGenerator.generateTo(output, Application::specTestModule)

        val document: JsonNode = mapper.readTree(output.toFile())
        assertTrue(document.path("paths").has("/items"))
    }

    @Test
    fun `runner exits even when application code leaves non-daemon threads`(@TempDir dir: Path) {
        val output = dir.resolve("openapi.json")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.papsign.ktor.openapigen.spec.GenerateSpecMain",
            "--module",
            "$module.specModuleWithNonDaemonThread",
            "--output",
            output.toString()
        ).redirectErrorStream(true).redirectOutput(dir.resolve("runner.log").toFile()).start()

        val finished = process.waitFor(2, TimeUnit.MINUTES)
        if (!finished) process.destroyForcibly()

        assertTrue(finished, "Runner did not exit")
        assertEquals(0, process.exitValue(), Files.readString(dir.resolve("runner.log")))
        assertTrue(Files.exists(output))
    }

    @Test
    fun `runner exits with failure status when the module fails`(@TempDir dir: Path) {
        val output = dir.resolve("openapi.json")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.papsign.ktor.openapigen.spec.GenerateSpecMain",
            "--module",
            "$module.specModuleWithoutOpenApiGen",
            "--output",
            output.toString()
        ).redirectErrorStream(true).redirectOutput(dir.resolve("runner.log").toFile()).start()

        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "Runner did not exit")
        assertEquals(1, process.exitValue())
        assertTrue(Files.readString(dir.resolve("runner.log")).contains("did not install OpenAPIGen"))
    }
}
