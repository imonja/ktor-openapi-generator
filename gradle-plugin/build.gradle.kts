import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("com.gradle.plugin-publish") version "1.3.0"
}

group = "io.github.imonja"
description = "Gradle plugin that generates the OpenAPI document of a Ktor application without starting a server"

// Same version scheme as the library build: -Pversion=X (CI) or 1.0.0-<commit> for local builds
if (project.hasProperty("releaseVersion")) {
    version = project.property("releaseVersion") as String
} else {
    version = project.findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: ("1.0.0-" + getCheckedOutGitCommitHash())
}

fun getCheckedOutGitCommitHash(): String {
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        return System.getenv("GITHUB_SHA")
    }
    return providers.exec { commandLine("git", "rev-parse", "--verify", "HEAD") }.standardOutput.asText.get().trim()
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter:6.0.1")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "functionalTestImplementation"(gradleTestKit())
}

// The plugin adds io.github.imonja:ktor-openapi-generator:<this version> to consumers
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/sources/version")
    val versionFile = outputDir.map { it.file("ktor-openapi-gradle-plugin.properties") }
    val pluginVersion = project.version.toString()

    inputs.property("version", pluginVersion)
    outputs.file(versionFile)

    doLast {
        val outputFile = versionFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("version=$pluginVersion\n")
    }
}

sourceSets {
    main {
        resources {
            srcDir(generateVersionProperties.map { it.outputs.files.singleFile.parentFile })
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Functional tests apply the plugin to a sample project that resolves the library from a local repository.
// Publish it first from the repository root:
//   ./gradlew publishAllPublicationsToFunctionalTestRepository -Pversion=<v>
//   ./gradlew :gradle-plugin:functionalTest -Pversion=<v>
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the plugin functional tests with Gradle TestKit."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    val libraryRepository = layout.projectDirectory.dir("../build/functional-test-repo")
    inputs.files(fileTree(libraryRepository)).withPropertyName("libraryRepository")
    systemProperty("functionalTest.libraryRepository", libraryRepository.asFile.absolutePath)
    systemProperty("functionalTest.libraryVersion", project.version.toString())
    systemProperty("functionalTest.gradleVersion", gradle.gradleVersion)
}

gradlePlugin {
    website = "https://github.com/imonja/ktor-openapi-generator"
    vcsUrl = "https://github.com/imonja/ktor-openapi-generator"
    testSourceSets(functionalTest)

    plugins {
        create("ktor-openapi") {
            id = "io.github.imonja.ktor-openapi"
            implementationClass = "io.github.imonja.ktor.openapi.plugin.KtorOpenApiPlugin"
            displayName = "Ktor OpenAPI Generator Gradle Plugin"
            description = "Generates the OpenAPI document of a Ktor application using ktor-openapi-generator " +
                "without starting a server, and verifies that the committed document is up to date"
            tags = listOf("ktor", "openapi", "swagger", "kotlin")
        }
    }
}

// Configure signing for Gradle Plugin Portal
signing {
    if (!version.toString().contains("SNAPSHOT")) {
        val signingKey = findProperty("signingInMemoryKey") as String?
        val signingPassword = findProperty("signingInMemoryKeyPassword") as String?
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Disable signing tasks for SNAPSHOT versions
if (version.toString().contains("SNAPSHOT")) {
    tasks.withType<Sign>().configureEach {
        enabled = false
    }
}

// GitHub Packages publishing configuration
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/imonja/ktor-openapi-generator")
            credentials {
                username = findProperty("github.name") as String? ?: System.getenv("GITHUB_USERNAME") ?: "x-access-token"
                password = findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Maven Central publishing configuration
mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "ktor-openapi-generator-gradle-plugin",
        version = project.version.toString()
    )

    pom {
        name.set("Ktor OpenAPI Generator Gradle Plugin")
        description.set(project.description)
        url.set("https://github.com/imonja/ktor-openapi-generator")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("imonja")
                name.set("imonja")
                url.set("https://github.com/imonja")
                organization.set("imonja")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/imonja/ktor-openapi-generator.git")
            developerConnection.set("scm:git:ssh://github.com:imonja/ktor-openapi-generator.git")
            url.set("https://github.com/imonja/ktor-openapi-generator")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

ktlint {
    filter {
        exclude { it.file.toString().contains("build") }
    }
}
