package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class KtorOpenApiExtension {
    /**
     * Fully qualified name of the Ktor module that installs OpenAPIGen and registers the documented routes,
     * in `ktor.application.modules` format, e.g. `com.example.OpenApiModuleKt.openApiModule`.
     */
    abstract val module: Property<String>

    /** Generated OpenAPI document. Defaults to `src/main/resources/openapi/openapi.json`. */
    abstract val outputFile: RegularFileProperty

    /** Regenerates the document whenever main classes are built. Defaults to `false`. */
    abstract val generateOnBuild: Property<Boolean>

    /** Adds `io.github.imonja:ktor-openapi-generator` with the plugin's version to `implementation`. Defaults to `true`. */
    abstract val addLibraryDependency: Property<Boolean>

    /** Maximum heap of the generator JVM. Defaults to `512m`. */
    abstract val maxHeapSize: Property<String>

    abstract val systemProperties: MapProperty<String, String>

    abstract val jvmArgs: ListProperty<String>
}
