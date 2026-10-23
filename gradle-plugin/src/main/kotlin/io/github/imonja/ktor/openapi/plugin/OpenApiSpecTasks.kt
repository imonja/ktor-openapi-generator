package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Runs the library's standalone generator in a separate JVM over the project's runtime classpath.
 *
 * Untracked: application code may read environment inputs Gradle cannot see, and Gradle must not delete
 * the committed document before a run that may fail. The generator replaces the file atomically on success.
 */
@UntrackedTask(because = "Application code may read inputs Gradle cannot track; the committed document must survive a failed run")
abstract class AbstractOpenApiSpecTask : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val module: Property<String>

    /** Name of the spec in `ktorOpenApi.specs`; absent for the tasks of `ktorOpenApi.module`. */
    @get:Input
    @get:Optional
    abstract val specName: Property<String>

    /** Only runs the tasks of `ktorOpenApi.specs`, which it depends on. */
    @get:Input
    abstract val aggregate: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val configurationError: Property<String>

    @get:Input
    abstract val maxHeapSize: Property<String>

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val systemProperties: MapProperty<String, String>

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    /** Committed OpenAPI document; not declared as an output so Gradle never cleans it up. */
    @get:Internal
    abstract val outputFile: RegularFileProperty

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @get:Internal
    abstract val resolvedDependencies: Property<ResolvedComponentResult>

    @get:Internal
    abstract val pluginVersion: Property<String>

    @get:ServiceReference(VersionWarningService.NAME)
    abstract val versionWarnings: Property<VersionWarningService>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    /** Fails on an inconsistent configuration; `false` when the task only aggregates the tasks of the specs. */
    protected fun shouldGenerate(): Boolean {
        configurationError.orNull?.let { throw GradleException(it) }
        return !aggregate.get()
    }

    protected fun generate(
        target: File,
    ) {
        if (!module.isPresent) {
            throw GradleException(
                specName.orNull?.let { "ktorOpenApi.specs.$it.module is not set. Point it at the Ktor module that installs OpenAPIGen" }
                    ?: (
                        "ktorOpenApi.module is not set. Point it at the Ktor module that installs OpenAPIGen, " +
                            "e.g. com.example.OpenApiModuleKt.openApiModule, " +
                            "or declare several documents in ktorOpenApi { specs { register(\"…\") { } } }"
                    ),
            )
        }
        val libraryVersion = LibraryVersionCheck.resolvedVersion(resolvedDependencies.get())
        LibraryVersionCheck
            .mismatchWarning(libraryVersion, pluginVersion.get())
            ?.takeIf { versionWarnings.get().firstTime(it) }
            ?.let { logger.warn("Warning: $it") }
        if (!LibraryVersionCheck.runnerOnClasspath(runtimeClasspath)) {
            throw GradleException(LibraryVersionCheck.missingRunnerMessage(libraryVersion, pluginVersion.get()))
        }

        val result = execOperations.javaexec {
            executable = javaLauncher
                .get()
                .executablePath.asFile.absolutePath
            classpath = runtimeClasspath
            mainClass.set(LibraryVersionCheck.RUNNER_MAIN_CLASS)
            args("--module", module.get(), "--output", target.absolutePath)
            maxHeapSize = this@AbstractOpenApiSpecTask.maxHeapSize.get()
            jvmArgs(this@AbstractOpenApiSpecTask.jvmArgs.get())
            systemProperties(this@AbstractOpenApiSpecTask.systemProperties.get())
            workingDir = workingDirectory.get().asFile
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            val source = specName.orNull?.let { "spec $it (module ${module.get()})" } ?: "module ${module.get()}"
            throw GradleException("OpenAPI document generation failed for $source; see the error output above.")
        }
    }
}

@UntrackedTask(because = "Generation must always run and must never delete the existing document before succeeding")
abstract class GenerateOpenApiSpecTask : AbstractOpenApiSpecTask() {
    @TaskAction
    fun run() {
        if (!shouldGenerate()) return
        val output = outputFile.get().asFile
        generate(output)
        logger.lifecycle("OpenAPI document written to ${output.relativeToOrSelf(workingDirectory.get().asFile)}")
    }
}

@UntrackedTask(because = "Compares a freshly generated document with the committed one on every run")
abstract class CheckOpenApiSpecTask : AbstractOpenApiSpecTask() {
    @get:Internal
    abstract val generateTaskPath: Property<String>

    @TaskAction
    fun run() {
        if (!shouldGenerate()) return
        val committed = outputFile.get().asFile
        val fresh = File(temporaryDir, committed.name)
        fresh.delete()
        generate(fresh)

        val location = committed.relativeToOrSelf(workingDirectory.get().asFile)
        if (!committed.isFile) {
            throw GradleException("OpenAPI document $location does not exist. Run ./gradlew ${generateTaskPath.get()} and commit the result.")
        }
        if (!committed.readBytes().contentEquals(fresh.readBytes())) {
            throw GradleException("OpenAPI document $location is out of date. Run ./gradlew ${generateTaskPath.get()} and commit the result.")
        }
    }
}
