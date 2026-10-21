package com.papsign.ktor.openapigen.modules.handlers

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.content.type.SelectedModule
import com.papsign.ktor.openapigen.model.base.PathItemModel
import com.papsign.ktor.openapigen.model.operation.OperationModel
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.ofType
import com.papsign.ktor.openapigen.modules.openapi.HandlerModule
import com.papsign.ktor.openapigen.modules.openapi.OperationModule
import com.papsign.ktor.openapigen.modules.providers.MethodProvider
import com.papsign.ktor.openapigen.modules.providers.PathProvider
import com.papsign.ktor.openapigen.route.modules.PathAliasModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale

object RouteHandler : HandlerModule {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun configure(apiGen: OpenAPIGen, provider: ModuleProvider<*>) {
        val methods = provider.ofType<MethodProvider>()
        if (methods.size > 1) error("API cannot have two methods simultaneously: ${methods.map { it.method.value }}")
        val paths = provider.ofType<PathProvider>()
        val path = "/${paths.flatMap { it.path.split('/').filter(String::isNotEmpty) }.joinToString("/")}"
        val operationModules = provider.ofType<OperationModule>()
        val aliases = provider.ofType<PathAliasModule>()
        apiGen.api.paths.getOrPut(path) { PathItemModel() }.also { pathItem ->
            methods.forEach {
                val name = it.method.value.lowercase(Locale.ROOT)
                // if (pathItem.containsKey(name)) error("$path::$name already defined")
                val op = pathItem.getOrPut(name) { OperationModel() } as OperationModel
                operationModules.forEach {
                    it.configure(apiGen, provider, op)
                }
                val operationId = op.operationId ?: generateOperationId(name, path)
                // Nested alias groups are numbered as one flat group, outermost alias first.
                op.operationId = aliases.firstOrNull()?.group?.operationId(
                    operationId,
                    aliases.fold(0) { index, alias -> index * alias.size + alias.index }
                ) ?: operationId
            }
        }
        log.trace(
            "Registered $path::${methods.map { it.method.value }} with OpenAPI description with ${provider.ofType<SelectedModule>().map { it.module::class.simpleName }}"
        )
    }

    private fun generateOperationId(method: String, path: String): String {
        val suffix = path.replace(Regex("\\{([^}]+)}")) { " By ${it.groupValues[1]} " }
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotEmpty)
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        return method + suffix.ifEmpty { "Root" }
    }
}
