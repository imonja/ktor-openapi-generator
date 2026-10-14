package com.papsign.ktor.openapigen.spec

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.papsign.ktor.openapigen.OpenAPIGen
import io.ktor.server.application.Application
import io.ktor.server.application.ServerConfigBuilder
import io.ktor.server.application.pluginOrNull
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Standalone OpenAPI generation: runs an application module on an engine that opens no sockets and
 * serializes the OpenAPI document registered by [OpenAPIGen].
 *
 * The module must install [OpenAPIGen] and register all documented routes before it returns.
 */
object OpenAPISpecGenerator {

    private const val STARTUP_TIMEOUT_MILLIS = 5 * 60 * 1000L

    /** Default mapper: pretty-printed JSON with two-space indentation and `"key": value` separators. */
    val defaultObjectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setDefaultPrettyPrinter(
            DefaultPrettyPrinter(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
                .withObjectIndenter(DefaultIndenter("  ", "\n"))
                .withArrayIndenter(DefaultIndenter("  ", "\n"))
        )
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Runs [module] and returns the OpenAPI document as JSON. */
    fun generate(module: Application.() -> Unit, objectMapper: ObjectMapper = defaultObjectMapper): String = render(objectMapper, moduleName = null) { module(module) }

    /**
     * Runs the module referenced by [moduleFqName] (Ktor `ktor.application.modules` format, e.g.
     * `com.example.ApplicationKt.module`) and returns the OpenAPI document as JSON.
     */
    fun generate(moduleFqName: String, objectMapper: ObjectMapper = defaultObjectMapper): String = render(objectMapper, moduleName = moduleFqName) {}

    /** Runs [module] and writes the OpenAPI document to [output]; an existing file is replaced only on success. */
    fun generateTo(output: Path, module: Application.() -> Unit, objectMapper: ObjectMapper = defaultObjectMapper) {
        writeAtomically(output, generate(module, objectMapper))
    }

    /** Runs the module referenced by [moduleFqName] and writes the OpenAPI document to [output]. */
    fun generateTo(output: Path, moduleFqName: String, objectMapper: ObjectMapper = defaultObjectMapper) {
        writeAtomically(output, generate(moduleFqName, objectMapper))
    }

    private fun render(objectMapper: ObjectMapper, moduleName: String?, configure: ServerConfigBuilder.() -> Unit): String {
        val environment = applicationEnvironment {
            config = MapApplicationConfig().apply {
                put("ktor.application.startupTimeoutMillis", STARTUP_TIMEOUT_MILLIS.toString())
                if (moduleName != null) put("ktor.application.modules", listOf(moduleName))
            }
        }
        val config = serverConfig(environment) {
            developmentMode = false
            watchPaths = emptyList()
            configure()
        }
        val server = embeddedServer(NoopApplicationEngine.Factory, config)
        try {
            try {
                server.start(wait = false)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
            val openApiGen = server.application.pluginOrNull(OpenAPIGen)
                ?: error("Module ${moduleName ?: "<lambda>"} did not install OpenAPIGen; install(OpenAPIGen) is required to generate the OpenAPI document")
            return objectMapper.writeValueAsString(openApiGen.api.serialize()) + "\n"
        } finally {
            server.stop(0, 0)
        }
    }

    private fun writeAtomically(output: Path, content: String) {
        val target = output.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".${target.fileName}", ".tmp")
        try {
            Files.writeString(temp, content)
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
