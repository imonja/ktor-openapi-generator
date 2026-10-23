package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/** One OpenAPI document of a project with several documents, declared in `ktorOpenApi { specs { register("name") { } } }`. */
abstract class OpenApiSpecDefinition
    @Inject
    constructor(
        private val name: String,
    ) : Named {
        override fun getName(): String = name

        /** Ktor module that installs OpenAPIGen and registers the routes of this document, in `ktor.application.modules` format. */
        abstract val module: Property<String>

        /** Generated OpenAPI document. Defaults to `src/main/resources/openapi/<name>.json`. */
        abstract val outputFile: RegularFileProperty
    }

internal object OpenApiSpecs {
    private val NAME = Regex("[A-Za-z][A-Za-z0-9]*")

    fun validateName(
        name: String,
    ) {
        if (!NAME.matches(name)) {
            throw InvalidUserDataException(
                "Invalid ktorOpenApi spec name '$name': use letters and digits only, starting with a letter, e.g. mobileAuth",
            )
        }
    }

    fun taskName(
        action: String,
        specName: String,
    ): String = action + specName.replaceFirstChar { it.uppercaseChar() } + "OpenApiSpec"

    /** Error shared by all tasks of the project, or `null` when the configuration is consistent. */
    fun configurationError(
        moduleSet: Boolean,
        specOutputs: Map<String, String>,
    ): String? {
        if (moduleSet && specOutputs.isNotEmpty()) {
            return "ktorOpenApi.module and ktorOpenApi.specs cannot be combined; move the module into specs { register(\"…\") { } }"
        }
        return specOutputs.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }
            .entries
            .firstOrNull()
            ?.let { (file, specs) ->
                "ktorOpenApi specs ${specs.joinToString(" and ")} write to the same file $file; give each spec its own outputFile"
            }
    }
}
