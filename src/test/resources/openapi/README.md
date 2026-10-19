# Official document validation schemas

These fixtures are unmodified schemas published by the OpenAPI Initiative:

- `3.0.schema.json`: https://spec.openapis.org/oas/3.0/schema/2024-10-18
- `3.1.schema.json`: https://spec.openapis.org/oas/3.1/schema/2026-08-03
- `3.2.schema.json`: https://spec.openapis.org/oas/3.2/schema/2025-09-17

The upstream schemas are licensed under the Apache License 2.0:
https://github.com/OAI/OpenAPI-Specification/blob/main/LICENSE

`OpenAPIVersionOpenAPITest` validates documents offline in every format. The
OpenAPI 3.1 and 3.2 document schemas intentionally accept arbitrary Schema Objects;
separate JSON Schema tests check the meaning of nullable types, enum constraints,
recursive references and required properties.
