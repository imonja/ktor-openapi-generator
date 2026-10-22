package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File
import java.util.zip.ZipFile

internal object LibraryVersionCheck {
    const val GROUP = "io.github.imonja"
    const val NAME = "ktor-openapi-generator"
    const val RUNNER_MAIN_CLASS = "com.papsign.ktor.openapigen.spec.GenerateSpecMain"
    private const val RUNNER_CLASS_FILE = "com/papsign/ktor/openapigen/spec/GenerateSpecMain.class"

    /** Version of the library selected on the classpath, or `null` when it is not a resolved module (absent, project or file dependency). */
    fun resolvedVersion(
        root: ResolvedComponentResult,
    ): String? {
        val seen = HashSet<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            val id = component.id
            if (id is ModuleComponentIdentifier && id.group == GROUP && id.module == NAME) return id.version
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
        }
        return null
    }

    fun mismatchWarning(
        resolvedVersion: String?,
        pluginVersion: String,
    ): String? {
        if (resolvedVersion == null || resolvedVersion == pluginVersion) return null
        return "$GROUP:$NAME $resolvedVersion is used on the runtime classpath, but the ktor-openapi plugin version is $pluginVersion. " +
            "Gradle selects the highest requested version, so a lower declared version is overridden by the plugin. " +
            "Use the same version for the plugin and the library."
    }

    fun missingRunnerMessage(
        resolvedVersion: String?,
        pluginVersion: String,
    ): String =
        if (resolvedVersion == null) {
            "$GROUP:$NAME is not on the runtime classpath. Add implementation(\"$GROUP:$NAME:$pluginVersion\") " +
                "or enable ktorOpenApi { addLibraryDependency.set(true) }."
        } else {
            "$GROUP:$NAME $resolvedVersion does not support standalone OpenAPI generation. Use version $pluginVersion."
        }

    fun runnerOnClasspath(
        classpath: Iterable<File>,
    ): Boolean =
        classpath.any { entry ->
            when {
                entry.isDirectory -> File(entry, RUNNER_CLASS_FILE).isFile
                entry.isFile && entry.name.endsWith(".jar") -> ZipFile(entry).use { it.getEntry(RUNNER_CLASS_FILE) != null }
                else -> false
            }
        }
}
