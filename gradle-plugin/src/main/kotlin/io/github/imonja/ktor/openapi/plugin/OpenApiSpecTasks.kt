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

    @get:Inject
    protected abstract val execOperations: ExecOperations

    protected fun generate(
        target: File,
    ) {
        if (!module.isPresent) {
            throw GradleException(
                "ktorOpenApi.module is not set. Point it at the Ktor module that installs OpenAPIGen, e.g. com.example.OpenApiModuleKt.openApiModule",
            )
        }
        val libraryVersion = LibraryVersionCheck.resolvedVersion(resolvedDependencies.get())
        LibraryVersionCheck.mismatchWarning(libraryVersion, pluginVersion.get())?.let { logger.warn("Warning: $it") }
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
            throw GradleException("OpenAPI document generation failed for module ${module.get()}; see the error output above.")
        }
    }
}

@UntrackedTask(because = "Generation must always run and must never delete the existing document before succeeding")
abstract class GenerateOpenApiSpecTask : AbstractOpenApiSpecTask() {
    @TaskAction
    fun run() {
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
