package com.papsign.ktor.openapigen

/** Serialization families and their default document versions. */
internal enum class OpenAPIVersion(val value: String) {
    V3_0("3.0.4"),
    V3_2("3.2.1");

    companion object {
        internal fun fromValue(value: String): OpenAPIVersion = when (value) {
            "3.0.0", "3.0.1", "3.0.2", "3.0.3", "3.0.4" -> V3_0
            "3.2.0", "3.2.1" -> V3_2
            else -> throw IllegalArgumentException(
                "Unsupported OpenAPI version '$value'. Supported versions: 3.0.0, 3.0.1, 3.0.2, 3.0.3, 3.0.4, 3.2.0, 3.2.1."
            )
        }
    }
}
