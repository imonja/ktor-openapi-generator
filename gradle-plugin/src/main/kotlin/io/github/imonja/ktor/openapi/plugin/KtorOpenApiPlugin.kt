package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.Properties

class KtorOpenApiPlugin : Plugin<Project> {
    override fun apply(
        project: Project,
    ) {
        val extension = project.extensions.create("ktorOpenApi", KtorOpenApiExtension::class.java).apply {
            outputFile.convention(project.layout.projectDirectory.file("src/main/resources/openapi/openapi.json"))
            generateOnBuild.convention(false)
            addLibraryDependency.convention(true)
            maxHeapSize.convention("512m")
            specs.configureEach {
                outputFile.convention(project.layout.projectDirectory.file("src/main/resources/openapi/$name.json"))
            }
        }
        project.pluginManager.withPlugin("java") { configure(project, extension) }
    }

    private fun configure(
        project: Project,
        extension: KtorOpenApiExtension,
    ) {
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
                },
            )
        }

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        val runtimeClasspath = project.configurations.named(main.runtimeClasspathConfigurationName)
        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val launcher = project.extensions.getByType(JavaToolchainService::class.java).launcherFor(toolchain)

        project.gradle.sharedServices.registerIfAbsent(VersionWarningService.NAME, VersionWarningService::class.java) {}
        val projectDirectory = project.layout.projectDirectory
        val configurationError = project.provider {
            val specOutputs = extension.specs.associate { spec ->
                spec.name to spec.outputFile
                    .get()
                    .asFile
                    .relativeToOrSelf(projectDirectory.asFile)
                    .invariantSeparatorsPath
            }
            OpenApiSpecs.configurationError(extension.module.isPresent, specOutputs)
        }
        val hasSpecs = project.provider { extension.specs.isNotEmpty() }

        val configureTask = { task: AbstractOpenApiSpecTask ->
            task.group = "documentation"
            task.runtimeClasspath.from(main.runtimeClasspath)
            task.configurationError.set(configurationError)
            task.maxHeapSize.set(extension.maxHeapSize)
            task.jvmArgs.set(extension.jvmArgs)
            task.systemProperties.set(extension.systemProperties)
            task.javaLauncher.set(launcher)
            task.workingDirectory.set(project.layout.projectDirectory)
            task.resolvedDependencies.set(runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent })
            task.pluginVersion.set(pluginVersion)
        }

        val generate = project.tasks.register("generateOpenApiSpec", GenerateOpenApiSpecTask::class.java) {
            description = "Generates the OpenAPI documents from the configured Ktor modules without starting a server."
            configureTask(this)
            module.set(extension.module)
            outputFile.set(extension.outputFile)
            aggregate.set(hasSpecs)
        }
        val check = project.tasks.register("checkOpenApiSpec", CheckOpenApiSpecTask::class.java) {
            description = "Fails when a committed OpenAPI document differs from a freshly generated one."
            configureTask(this)
            module.set(extension.module)
            outputFile.set(extension.outputFile)
            aggregate.set(hasSpecs)
            generateTaskPath.set(generate.map { it.path })
            mustRunAfter(generate)
        }

        extension.specs.all {
            val spec = this
            OpenApiSpecs.validateName(spec.name)
            val specGenerate = project.tasks.register(OpenApiSpecs.taskName("generate", spec.name), GenerateOpenApiSpecTask::class.java) {
                description = "Generates the ${spec.name} OpenAPI document without starting a server."
                configureTask(this)
                specName.set(spec.name)
                module.set(spec.module)
                outputFile.set(spec.outputFile)
                aggregate.set(false)
            }
            val specCheck = project.tasks.register(OpenApiSpecs.taskName("check", spec.name), CheckOpenApiSpecTask::class.java) {
                description = "Fails when the committed ${spec.name} OpenAPI document differs from a freshly generated one."
                configureTask(this)
                specName.set(spec.name)
                module.set(spec.module)
                outputFile.set(spec.outputFile)
                aggregate.set(false)
                // Regenerating every document at once also fixes the stale ones a failed build did not reach
                generateTaskPath.set(generate.map { it.path })
                mustRunAfter(specGenerate)
            }
            generate.configure { dependsOn(specGenerate) }
            check.configure { dependsOn(specCheck) }
        }

        project.tasks.named("check") { dependsOn(check) }
        project.tasks.named(main.classesTaskName) {
            finalizedBy(extension.generateOnBuild.map { enabled -> if (enabled) listOf(generate) else emptyList() })
        }
    }

    private fun pluginVersion(): String {
        val properties = Properties()
        KtorOpenApiPlugin::class.java.classLoader
            .getResourceAsStream("ktor-openapi-gradle-plugin.properties")
            ?.use { properties.load(it) }
            ?: error("ktor-openapi-gradle-plugin.properties is missing from the plugin jar")
        return properties.getProperty("version")
    }
}
