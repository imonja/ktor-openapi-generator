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
- Kotlin 2.3.0+
- Ktor 3.4.0+

Current baseline used in this project: Kotlin 2.3.0 and Ktor 3.4.0.

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

Full minimal example: [`src/test/kotlin/com/papsign/ktor/openapigen/examples/MinimalExample.kt`](src/test/kotlin/com/papsign/ktor/openapigen/examples/MinimalExample.kt)

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

## OpenAPI Version

The default document format is **OpenAPI 3.2.1**. To use the compatibility format:

```kotlin
install(OpenAPIGen) {
    openApiVersion = "3.0.1"
}
```

Supported values: `3.0.0`, `3.0.1`, `3.0.2`, `3.0.3`, `3.0.4`, `3.2.0` and
`3.2.1`. The exact string is preserved in the document's `openapi` field;
versions in the same family share the corresponding serialization rules.
Unsupported values fail immediately when assigned to `openApiVersion`.
`info.version` is the version of your own API and is independent of this setting.

In 3.2 documents, nullable types use JSON Schema type unions, nullable DTO
references use `anyOf`, and nullable enums include `null`. Schema examples use
`examples`; base64 strings use `contentEncoding: base64`. Raw binary schemas
have no JSON string type and use `contentMediaType` to identify file uploads in
Swagger UI. For an inline binary body this matches the declared media type;
for a DTO file property it defaults to `application/octet-stream`.
Parameter, media type and named examples retain their original representation.
Parameter defaults are emitted at `schema.default`, as required by OpenAPI,
instead of as a top-level parameter property.

This changes the default document format for existing applications. Select
`openApiVersion = "3.0.4"` (or another supported 3.0 version) if a consumer
requires OpenAPI 3.0. Existing direct assignments such as
`api.openapi = "3.0.0"` still select the 3.0 serialization rules. Unsupported
versions fail explicitly; assigning an arbitrary version string does not add
support for its dialect. The same rules apply to `api.serialize()` for standalone
document generation. HTTP request parsing and response handling are unchanged.

This release migrates the existing feature set. Future features and priorities
are described in [the OpenAPI development proposals](docs/design/openapi-evolution.md).

## Swagger UI Version

The bundled Swagger UI version is **5.33.2**, which includes OpenAPI 3.2 support.
At runtime, the version is read from the WebJar's Maven metadata, with a matching
fallback in `SwaggerUIVersion`. The dependency is declared in `build.gradle.kts`;
`src/main/resources/version.properties` records the same version.

Overriding `swaggerUiVersion` requires the corresponding WebJar resources on the
classpath. For 3.2 documents, use a Swagger UI release with 3.2 support.

## Development

See [the test guide](src/test/README.md) for the package layout, fixture conventions, and focused test commands.

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
