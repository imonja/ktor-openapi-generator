import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    `maven-publish`
    signing
    id("net.nemerosa.versioning") version "3.1.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "io.github.imonja"
base.archivesName.set("ktor-open-api")
version = project.findProperty("version")?.toString() ?: ("1.0.0-" + getCheckedOutGitCommitHash())

fun runCommand(command: String): String {
    val execResult = providers.exec {
        commandLine(command.split("\\s".toRegex()))
    }.standardOutput.asText

    return execResult.get()
}

fun getCheckedOutGitCommitHash(): String {
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        return System.getenv("GITHUB_SHA")
    }
    return runCommand("git rev-parse --verify HEAD")
}

val ktorVersion = "3.3.3"
val swaggerUiVersion = "5.30.3"
val junitVersjon = "6.0.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("org.slf4j:slf4j-api:2.0.17")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1") // needed for multipart parsing
    // when updating the version here, don't forge to update version in OpenAPIGen.kt line 68
    api("org.webjars:swagger-ui:$swaggerUiVersion")

    implementation("org.reflections:reflections:0.10.2") // only used while initializing

    // testing
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-auth:$ktorVersion")
    testImplementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("stdlib-jdk8"))

    testImplementation("ch.qos.logback:logback-classic:1.5.21") // logging framework for the tests

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersjon") // junit testing framework
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersjon") // generated parameters for tests
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersjon") // testing runtime
}

tasks {
    withType<Test> {
        reports.html.required.set(false)
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
}

//dokka {
//    moduleName.set("Ktor OpenAPI/Swagger 3 Generator")
//    dokkaPublications.html {
//        outputDirectory.set(layout.buildDirectory.dir("docs"))
//    }
//    dokkaSourceSets.main {
//        sourceLink {
//            localDirectory.set(file("src/main/kotlin"))
//            remoteUrl("https://github.com/navikt/ktor-openapi-generator/tree/master/src/main/kotlin")
//            remoteLineSuffix.set("#L")
//        }
//    }
//}

// ------------------------------------ Deployment Configuration  ------------------------------------
// deployment configuration - deploy with sources and documentation
val sourcesJar by tasks.registering(Jar::class, fun Jar.() {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
})

val javadocJar by tasks.registering(Jar::class, fun Jar.() {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
})

// name the publication as it is referenced
val publication = "mavenJava"
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/imonja/ktor-openapi-generator")
            credentials {
                username = "x-access-token"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    // create jar with sources and with javadoc
    publications {
        create<MavenPublication>(publication) {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("Ktor OpenAPI/Swagger 3 Generator")
                description.set("The Ktor OpenAPI Generator is a library to automatically generate the descriptor as you route your ktor application.")
                url.set("https://github.com/imonja/ktor-openapi-generator")
                packaging = "jar"
                licenses {
                    license {
                        name.set("Apache-2.0 License")
                        url.set("https://github.com/imonja/ktor-openapi-generator/blob/master/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/imonja/ktor-openapi-generator.git")
                    url.set("https://github.com/imonja/ktor-openapi-generator")
                }
            }
        }
    }
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

// Pass på at når vi kaller JavaExec eller Test tasks så bruker vi samme språk-versjon som vi kompilerer til
val toolchainLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<Test>().configureEach { javaLauncher.set(toolchainLauncher) }
tasks.withType<JavaExec>().configureEach { javaLauncher.set(toolchainLauncher) }

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude { it.file.toString().contains("build") }
    }
}

tasks.register<Copy>("setupHooks") {
    description = "Setup git hooks for development"
    group = "git hooks"
    outputs.upToDateWhen { false }
    from("$rootDir/scripts/pre-commit")
    into("$rootDir/.git/hooks/")

    doLast {
        // Make the hook executable
        file("$rootDir/.git/hooks/pre-commit").setExecutable(true)
        println("✅ Pre-commit hook installed successfully")
        println("   Hook will run ktlintCheck")
        println("   Use 'git commit --no-verify' to skip the hook if needed")
        println("")
        println("💡 Available commands:")
        println("   ./gradlew ktlintFormat     - Auto-fix main modules")
    }
}

// Auto-install git hooks on the first run
gradle.taskGraph.whenReady {
    val preCommitHook = file("$rootDir/.git/hooks/pre-commit")
    val sourceHook = file("$rootDir/scripts/pre-commit")

    // Check if we need to install/update the hook
    val needsInstall = !preCommitHook.exists() ||
        !sourceHook.exists() ||
        (
            preCommitHook.exists() && sourceHook.exists() &&
                preCommitHook.readText() != sourceHook.readText()
            )

    if (needsInstall && file("$rootDir/.git").exists() && sourceHook.exists()) {
        println("🔧 Auto-installing git hooks...")
        copy {
            from("$rootDir/scripts/pre-commit")
            into("$rootDir/.git/hooks/")
        }
        file("$rootDir/.git/hooks/pre-commit").setExecutable(true)
        println("✅ Git hooks auto-installed")
    }
}
