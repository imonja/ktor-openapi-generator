[![License](https://img.shields.io/github/license/imonja/ktor-openapi-generator)](LICENSE)
[![Release](https://img.shields.io/github/v/release/imonja/ktor-openapi-generator)](https://github.com/imonja/ktor-openapi-generator/releases)

# Ktor OpenAPI Generator

Library for building Ktor routes and OpenAPI 3 documentation together.

As you define typed routes, request/response models, parameters, auth, and tags, the plugin keeps the OpenAPI schema in sync and can serve Swagger UI.

## Installation

```kotlin
dependencies {
    implementation("io.github.imonja:ktor-openapi-generator:<version>")
}
```

## Requirements

- JDK 21+
- Kotlin 2.2+
- Ktor 3.3+

## Quick Start

```kotlin
fun Application.minimalExample() {
    install(OpenAPIGen) {
        serveOpenApiJson = true       // /openapi.json
        serveSwaggerUi = true         // /swagger-ui/index.html
        info {
            title = "Minimal Example API"
            version = "1.0.0"
        }
    }

    install(ContentNegotiation) {
        jackson()
    }

    apiRouting {
        route("/example/{name}") {
            post<SomeParams, SomeResponse, SomeRequest> { params, body ->
                respond(SomeResponse("Hello ${params.name}! From body: ${body.foo}."))
            }
        }
    }
}

data class SomeParams(@param:PathParam("who to say hello") val name: String)
data class SomeRequest(val foo: String)
data class SomeResponse(val bar: String)
```

Full minimal example: [`src/test/kotlin/MinimalExample.kt`](src/test/kotlin/MinimalExample.kt)

## What You Get

- OpenAPI 3 schema generation from typed Ktor routes
- Swagger UI serving from the plugin
- Typed request/response support with Ktor content negotiation
- Parameter parsing strategies (path/query/header)
- Auth integration with strongly typed principals
- Route tags, explicit statuses, and documented exceptions
- Multipart and binary payload support
- Validation and schema annotations

## Runtime Endpoints

Default plugin endpoints:

- OpenAPI JSON: `/openapi.json`
- Swagger UI: `/swagger-ui/index.html`

Both are configurable via `install(OpenAPIGen) { ... }`:

- `openApiJsonPath`
- `swaggerUiPath`
- `serveOpenApiJson`
- `serveSwaggerUi`
- `swaggerUiVersion`

## Swagger UI Version

Default Swagger UI version is loaded from:

- [`src/main/resources/version.properties`](src/main/resources/version.properties)

Current value:

```properties
swagger.ui.version=5.30.3
```

## Development

Useful local commands:

```bash
./gradlew setupHooks
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew test
./gradlew build
./gradlew dokkaGenerateHtml
```

Tag helper commands:

```bash
make last-tag
make add-tag-and-push
make delete-tag
```

## CI and Publishing

GitHub Actions workflows:

- Pull requests: lint + tests
- `main`/tags: lint + tests + publish pipeline

Local publication:

```bash
./gradlew publishToMavenLocal
```

For Maven Central/GPG publishing credentials, see comments in [`gradle.properties`](gradle.properties).

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
