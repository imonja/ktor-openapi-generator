package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.Properties

class KtorOpenApiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("ktorOpenApi", KtorOpenApiExtension::class.java).apply {
            outputFile.convention(project.layout.projectDirectory.file("src/main/resources/openapi/openapi.json"))
            generateOnBuild.convention(false)
            addLibraryDependency.convention(true)
            maxHeapSize.convention("512m")
        }
        project.pluginManager.withPlugin("java") { configure(project, extension) }
    }

    private fun configure(project: Project, extension: KtorOpenApiExtension) {
        val pluginVersion = pluginVersion()
        val dependencies = project.dependencies
        project.configurations.named("implementation") {
            this.dependencies.addAllLater(
                extension.addLibraryDependency.map { enabled ->
                    if (enabled) {
                        listOf<Dependency>(dependencies.create("${LibraryVersionCheck.GROUP}:${LibraryVersionCheck.NAME}:$pluginVersion"))
                    } else {
                        emptyList()
                    }
                }
            )
        }

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        val runtimeClasspath = project.configurations.named(main.runtimeClasspathConfigurationName)
        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val launcher = project.extensions.getByType(JavaToolchainService::class.java).launcherFor(toolchain)

        val configureTask = { task: AbstractOpenApiSpecTask ->
            task.group = "documentation"
            task.runtimeClasspath.from(main.runtimeClasspath)
            task.module.set(extension.module)
            task.outputFile.set(extension.outputFile)
            task.maxHeapSize.set(extension.maxHeapSize)
            task.jvmArgs.set(extension.jvmArgs)
            task.systemProperties.set(extension.systemProperties)
            task.javaLauncher.set(launcher)
            task.workingDirectory.set(project.layout.projectDirectory)
            task.resolvedDependencies.set(runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent })
            task.pluginVersion.set(pluginVersion)
        }

        val generate = project.tasks.register("generateOpenApiSpec", GenerateOpenApiSpecTask::class.java) {
            description = "Generates the OpenAPI document from the configured Ktor module without starting a server."
            configureTask(this)
        }
        val check = project.tasks.register("checkOpenApiSpec", CheckOpenApiSpecTask::class.java) {
            description = "Fails when the committed OpenAPI document differs from a freshly generated one."
            configureTask(this)
            generateTaskPath.set(generate.map { it.path })
            mustRunAfter(generate)
        }

        project.tasks.named("check") { dependsOn(check) }
        project.tasks.named(main.classesTaskName) {
            finalizedBy(extension.generateOnBuild.map { enabled -> if (enabled) listOf(generate) else emptyList() })
        }
    }

    private fun pluginVersion(): String {
        val properties = Properties()
        KtorOpenApiPlugin::class.java.classLoader.getResourceAsStream("ktor-openapi-gradle-plugin.properties")
            ?.use { properties.load(it) }
            ?: error("ktor-openapi-gradle-plugin.properties is missing from the plugin jar")
        return properties.getProperty("version")
    }
}
