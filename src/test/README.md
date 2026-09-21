# Tests

All tests use the existing `test` source set and live under
`kotlin/com/papsign/ktor/openapigen`.

| Package | Responsibility |
| --- | --- |
| `routing` | Routes, aliases, path annotations, tags, generic handlers, and the minimal example smoke tests |
| `parameters/defaults` | Parameter defaults in HTTP requests and OpenAPI documents |
| `parameters/enums` | Strict and non-strict enum parsing through HTTP |
| `parameters/parsers` | Focused parser tests, organized by builder and query style, plus parser assertion helpers |
| `parameters/validation` | Query, path, header, and optional parameter validation |
| `schema` | Schema properties, polymorphism, and Kotlin type reflection |
| `content` | Request body requirements, form documentation, binary content, and multipart parsing |
| `content/validation` | String, number, nested, optional, and phone request body validation |
| `auth` | Authentication documentation and its JWT application fixture |
| `support` | Shared application and plugin configuration |
| `examples` | Demonstration applications and their models |

## Adding tests

- Place a test in its functional area and match its package to its directory.
- Name document-generation tests `*OpenAPITest`; name HTTP behavior and parser tests `*Test`.
- Match the test class name to the filename. Give independent scenarios separate test methods so each failure is reported independently.
- Keep models and routes used by one class in that file. Use a nearby `*Fixtures.kt` for setup shared by several tests in the same area.
- Put configuration shared across areas in `support`. `installOpenAPI()` and `installJackson()` provide minimal plugin setup; `setupBaseTestServer()` additionally configures JSON formatting, error handling, and the OpenAPI endpoint.
- Start a fresh `testApplication` for each HTTP scenario. Keep feature-specific routes in their own fixtures.

## Running tests

```bash
# Entire suite
./gradlew test

# One functional area
./gradlew test --tests 'com.papsign.ktor.openapigen.parameters.validation.*'

# One class
./gradlew test --tests '*StringRequestBodyValidationTest'

# Repository formatting checks
./gradlew ktlintCheck
```

Executable examples can be launched from their `main` methods in the IDE:
`examples.Basic`, `examples.Minimal`, `examples.TestServer`, and `examples.JwtExample`
(all under `com.papsign.ktor.openapigen`). The minimal application module used by
the quick start is in `examples/MinimalExample.kt`.
