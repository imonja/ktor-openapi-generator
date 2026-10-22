package com.papsign.ktor.openapigen

/** Serialization families and their default (latest) document versions. 3.1 shares the 3.2 serialization rules. */
internal enum class OpenAPIVersion(
    val value: String,
    val versions: List<String>,
) {
    V3_0("3.0.4", listOf("3.0.0", "3.0.1", "3.0.2", "3.0.3", "3.0.4")),
    V3_1("3.1.2", listOf("3.1.0", "3.1.1", "3.1.2")),
    V3_2("3.2.1", listOf("3.2.0", "3.2.1")),
    ;

    /** The `major.minor` form, e.g. `3.1`, which selects [value]. */
    val shortValue: String get() = value.substringBeforeLast('.')

    companion object {
        /** Exact versions are kept as is; a `major.minor` form resolves to the latest version of its family. */
        internal fun resolve(
            value: String,
        ): String = entries.firstOrNull { it.shortValue == value }?.value ?: value.also { fromValue(it) }

        internal fun fromValue(
            value: String,
        ): OpenAPIVersion =
            entries.firstOrNull { value in it.versions }
                ?: throw IllegalArgumentException(
                    "Unsupported OpenAPI version '$value'. Supported versions: " +
                        entries.joinToString { it.shortValue } + " (latest of the family) or " +
                        entries.flatMap { it.versions }.joinToString() + ".",
                )
    }
}
