rootProject.name = "ktor-openapi-generator"

// pluginManagement {
//    repositories {
//        gradlePluginPortal()
//    }
// }
//
// dependencyResolutionManagement {
//    // Felles for alle gradle prosjekter i repoet
//    @Suppress("UnstableApiUsage")
//    repositories {
//        mavenCentral()
//        mavenLocal()
//    }
// }

// Gradle plugin that generates the OpenAPI document at build time; built and published separately
includeBuild("gradle-plugin")
