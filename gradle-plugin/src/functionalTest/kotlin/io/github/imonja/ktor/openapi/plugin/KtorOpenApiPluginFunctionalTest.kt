package io.github.imonja.ktor.openapi.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorOpenApiPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val output get() = File(projectDir, "src/main/resources/openapi/openapi.json")

    private fun setUpProject(
        extension: String = "",
        dependencies: String = "",
        module: String = API_MODULE,
        topLevelModule: String = "module.set(\"sample.ApiKt.api\")",
    ) {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"sample\"\n")
        File(projectDir, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx1g\n")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.imonja.ktor-openapi")
            }

            repositories {
                maven { url = uri("${libraryRepository.invariantSeparatorsPath}") }
                mavenCentral()
            }

            dependencies {
                implementation("io.ktor:ktor-server-core:3.4.0")
                $dependencies
            }

            ktorOpenApi {
                $topLevelModule
                $extension
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/kotlin/sample/Api.kt").apply { parentFile.mkdirs() }.writeText(module)
    }

    private fun runner(
        vararg arguments: String,
    ): GradleRunner =
        GradleRunner
            .create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace", "--configuration-cache")
            .forwardOutput()

    private fun build(
        vararg arguments: String,
    ): BuildResult = runner(*arguments).build()

    private fun buildAndFail(
        vararg arguments: String,
    ): BuildResult = runner(*arguments).buildAndFail()

    @Test
    fun `generates the document using the library added by the plugin`() {
        setUpProject()

        val result = build("generateOpenApiSpec")

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateOpenApiSpec")?.outcome)
        val document = output.readText()
        assertTrue(document.contains("\"/items\""), document)
        assertTrue(document.contains("\"title\": \"Sample API\""), document)
        assertFalse(result.output.contains("is used on the runtime classpath"), "unexpected version warning")
    }

    @Test
    fun `generation always runs and reuses the configuration cache`() {
        setUpProject()
        build("generateOpenApiSpec")

        val second = build("generateOpenApiSpec")

        assertEquals(TaskOutcome.SUCCESS, second.task(":generateOpenApiSpec")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache"), "configuration cache was not reused")
    }

    @Test
    fun `writes to a custom output file with system properties`() {
        setUpProject(
            extension =
                """
                outputFile.set(layout.projectDirectory.file("docs/api.json"))
                systemProperties.put("sample.title", "From Gradle")
                """.trimIndent(),
            module = API_MODULE.replace("title = \"Sample API\"", "title = System.getProperty(\"sample.title\")"),
        )

        build("generateOpenApiSpec")

        assertTrue(File(projectDir, "docs/api.json").readText().contains("\"title\": \"From Gradle\""))
        assertFalse(output.exists())
    }

    @Test
    fun `check passes for an up to date document and fails for a stale one`() {
        setUpProject()
        build("generateOpenApiSpec")

        assertEquals(TaskOutcome.SUCCESS, build("check").task(":checkOpenApiSpec")?.outcome)

        output.writeText(output.readText().replace("Sample API", "Stale API"))
        val failure = buildAndFail("check")
        assertTrue(failure.output.contains("is out of date. Run ./gradlew :generateOpenApiSpec"), failure.output)
        assertTrue(output.readText().contains("Stale API"), "check must not modify the committed document")
    }

    @Test
    fun `check fails when the document does not exist`() {
        setUpProject()

        val failure = buildAndFail("checkOpenApiSpec")

        assertTrue(failure.output.contains("does not exist. Run ./gradlew :generateOpenApiSpec"), failure.output)
    }

    @Test
    fun `failed generation keeps the existing document`() {
        setUpProject(module = API_MODULE.replace("install(OpenAPIGen)", "error(\"boom\"); install(OpenAPIGen)"))
        output.apply { parentFile.mkdirs() }.writeText("previous")

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("boom"), failure.output)
        assertTrue(failure.output.contains("OpenAPI document generation failed for module sample.ApiKt.api"), failure.output)
        assertEquals("previous", output.readText())
    }

    @Test
    fun `generateOnBuild regenerates the document after compiling`() {
        setUpProject(extension = "generateOnBuild.set(true)")

        val result = build("classes")

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateOpenApiSpec")?.outcome)
        assertTrue(output.exists())
    }

    @Test
    fun `classes does not generate by default`() {
        setUpProject()

        val result = build("classes")

        assertEquals(null, result.task(":generateOpenApiSpec"))
        assertFalse(output.exists())
    }

    @Test
    fun `missing library is reported when automatic dependency is disabled`() {
        setUpProject(extension = "addLibraryDependency.set(false)", module = PLAIN_MODULE)

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("io.github.imonja:ktor-openapi-generator is not on the runtime classpath"), failure.output)
    }

    @Test
    fun `different library version is reported`() {
        setUpProject(
            extension = "addLibraryDependency.set(false)",
            dependencies = "implementation(\"io.github.imonja:ktor-openapi-generator:0.3.3\")",
            module = PLAIN_MODULE,
        )

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("ktor-openapi-generator 0.3.3 is used on the runtime classpath"), failure.output)
        assertTrue(failure.output.contains("0.3.3 does not support standalone OpenAPI generation"), failure.output)
    }

    @Test
    fun `higher explicit version wins over the added dependency with a warning`() {
        // 0.3.3 is higher than the 0.0.0-SNAPSHOT used by the functional tests
        setUpProject(dependencies = "implementation(\"io.github.imonja:ktor-openapi-generator:0.3.3\")", module = PLAIN_MODULE)

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("ktor-openapi-generator 0.3.3 is used on the runtime classpath"), failure.output)
    }

    @Test
    fun `explicit dependency on the plugin version produces no warning`() {
        setUpProject(dependencies = "implementation(\"io.github.imonja:ktor-openapi-generator:$libraryVersion\")")

        val result = build("generateOpenApiSpec")

        assertFalse(result.output.contains("is used on the runtime classpath"), "unexpected version warning")
    }

    private fun setUpSpecs(
        specs: String = TWO_SPECS,
        extension: String = "",
    ) = setUpProject(
        topLevelModule = "",
        extension = "$extension\nspecs {\n$specs\n}",
        module = API_MODULE + "\n\n" + ADMIN_MODULE,
    )

    private fun specFile(
        name: String,
    ) = File(projectDir, "src/main/resources/openapi/$name.json")

    @Test
    fun `generateOpenApiSpec writes every spec and a spec task writes only its own`() {
        setUpSpecs()

        val single = build("generateAdminOpenApiSpec")

        assertEquals(TaskOutcome.SUCCESS, single.task(":generateAdminOpenApiSpec")?.outcome)
        assertEquals(null, single.task(":generatePublicOpenApiSpec"))
        assertTrue(File(projectDir, "docs/admin-api.json").readText().contains("\"title\": \"Admin API\""))
        assertFalse(specFile("public").exists())

        val all = build("generateOpenApiSpec")

        assertEquals(TaskOutcome.SUCCESS, all.task(":generatePublicOpenApiSpec")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, all.task(":generateAdminOpenApiSpec")?.outcome)
        assertTrue(specFile("public").readText().contains("\"title\": \"Sample API\""))
        assertFalse(output.exists())
    }

    @Test
    fun `check runs every spec and names the aggregate task for a stale one`() {
        setUpSpecs()
        build("generateOpenApiSpec")

        val passed = build("check")

        assertEquals(TaskOutcome.SUCCESS, passed.task(":checkPublicOpenApiSpec")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, passed.task(":checkAdminOpenApiSpec")?.outcome)

        val admin = File(projectDir, "docs/admin-api.json")
        admin.writeText(admin.readText().replace("Admin API", "Stale API"))
        val failure = buildAndFail("checkOpenApiSpec")

        assertTrue(failure.output.contains("docs/admin-api.json is out of date. Run ./gradlew :generateOpenApiSpec"), failure.output)
    }

    @Test
    fun `specs reuse the configuration cache`() {
        setUpSpecs()
        build("generateOpenApiSpec")
        build("checkOpenApiSpec")

        val second = build("checkOpenApiSpec")

        assertTrue(second.output.contains("Reusing configuration cache"), "configuration cache was not reused")
        assertEquals(TaskOutcome.SUCCESS, second.task(":checkAdminOpenApiSpec")?.outcome)
    }

    @Test
    fun `continue reports every stale spec`() {
        setUpSpecs()
        build("generateOpenApiSpec")
        specFile("public").writeText("stale")
        File(projectDir, "docs/admin-api.json").writeText("stale")

        val failure = buildAndFail("checkOpenApiSpec", "--continue")

        assertTrue(failure.output.contains("public.json is out of date"), failure.output)
        assertTrue(failure.output.contains("admin-api.json is out of date"), failure.output)
    }

    @Test
    fun `generateOnBuild regenerates every spec`() {
        setUpSpecs(extension = "generateOnBuild.set(true)")

        build("classes")

        assertTrue(specFile("public").exists())
        assertTrue(File(projectDir, "docs/admin-api.json").exists())
    }

    @Test
    fun `version warning is logged once for several specs`() {
        setUpSpecs()
        File(projectDir, "build.gradle.kts").appendText(
            "\ndependencies { implementation(\"io.github.imonja:ktor-openapi-generator:0.3.3\") }\n",
        )

        val failure = runner("generateOpenApiSpec", "--continue").buildAndFail()

        assertEquals(1, Regex("0.3.3 is used on the runtime classpath").findAll(failure.output).count(), failure.output)
    }

    @Test
    fun `module and specs cannot be combined`() {
        setUpProject(extension = "specs { register(\"admin\") { module.set(\"sample.ApiKt.api\") } }")

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("ktorOpenApi.module and ktorOpenApi.specs cannot be combined"), failure.output)
    }

    @Test
    fun `spec without a module is reported`() {
        setUpSpecs(specs = "register(\"admin\")")

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("ktorOpenApi.specs.admin.module is not set"), failure.output)
    }

    @Test
    fun `missing module mentions specs`() {
        setUpProject(topLevelModule = "")

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(failure.output.contains("ktorOpenApi.module is not set"), failure.output)
        assertTrue(failure.output.contains("specs { register("), failure.output)
    }

    @Test
    fun `specs writing to the same file are reported`() {
        setUpSpecs(
            specs =
                """
                register("public") { module.set("sample.ApiKt.api") }
                register("admin") {
                    module.set("sample.ApiKt.admin")
                    outputFile.set(layout.projectDirectory.file("src/main/resources/openapi/public.json"))
                }
                """.trimIndent(),
        )

        val failure = buildAndFail("generateOpenApiSpec")

        assertTrue(
            failure.output.contains("ktorOpenApi specs admin and public write to the same file src/main/resources/openapi/public.json"),
            failure.output,
        )
    }

    @Test
    fun `invalid spec name is rejected when registered`() {
        setUpSpecs(specs = "register(\"mobile-auth\") { module.set(\"sample.ApiKt.api\") }")

        val failure = buildAndFail("help")

        assertTrue(failure.output.contains("Invalid ktorOpenApi spec name 'mobile-auth'"), failure.output)
    }

    @Test
    fun `failed spec generation names the spec`() {
        setUpSpecs(specs = "register(\"broken\") { module.set(\"sample.ApiKt.missing\") }")

        val failure = buildAndFail("generateBrokenOpenApiSpec")

        assertTrue(failure.output.contains("generation failed for spec broken (module sample.ApiKt.missing)"), failure.output)
    }

    companion object {
        private val libraryRepository = File(System.getProperty("functionalTest.libraryRepository"))
        private val libraryVersion: String = System.getProperty("functionalTest.libraryVersion")
        private val gradleVersion: String = System.getProperty("functionalTest.gradleVersion")

        @JvmStatic
        @BeforeAll
        fun requireLibraryRepository() {
            check(File(libraryRepository, "io/github/imonja/ktor-openapi-generator/$libraryVersion").isDirectory) {
                "ktor-openapi-generator $libraryVersion is not published to $libraryRepository. " +
                    "Run ./gradlew publishAllPublicationsToFunctionalTestRepository -Pversion=$libraryVersion from the repository root first."
            }
        }

        private val API_MODULE =
            """
            package sample

            import com.papsign.ktor.openapigen.OpenAPIGen
            import com.papsign.ktor.openapigen.route.apiRouting
            import com.papsign.ktor.openapigen.route.path.normal.get
            import com.papsign.ktor.openapigen.route.response.respond
            import com.papsign.ktor.openapigen.route.route
            import io.ktor.server.application.Application
            import io.ktor.server.application.install

            data class Item(val name: String)

            fun Application.api() {
                install(OpenAPIGen) {
                    info {
                        title = "Sample API"
                        version = "1.0.0"
                    }
                }
                apiRouting {
                    route("items") {
                        get<Unit, List<Item>> { respond(emptyList()) }
                    }
                }
            }
            """.trimIndent()

        private val TWO_SPECS =
            """
            register("public") { module.set("sample.ApiKt.api") }
            register("admin") {
                module.set("sample.ApiKt.admin")
                outputFile.set(layout.projectDirectory.file("docs/admin-api.json"))
            }
            """.trimIndent()

        // Appended to API_MODULE, so it reuses its imports and Item
        private val ADMIN_MODULE =
            """
            fun Application.admin() {
                install(OpenAPIGen) {
                    info {
                        title = "Admin API"
                        version = "1.0.0"
                    }
                }
                apiRouting {
                    route("admin/items") {
                        get<Unit, List<Item>> { respond(emptyList()) }
                    }
                }
            }
            """.trimIndent()

        private val PLAIN_MODULE =
            """
            package sample

            import io.ktor.server.application.Application

            fun Application.api() = Unit
            """.trimIndent()
    }
}
