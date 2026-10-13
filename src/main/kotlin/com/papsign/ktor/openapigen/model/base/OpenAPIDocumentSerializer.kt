package com.papsign.ktor.openapigen.model.base

import com.papsign.ktor.openapigen.OpenAPIVersion

/** Converts schema locations only; examples, defaults and extension payloads are application data. */
internal class OpenAPIDocumentSerializer(private val version: OpenAPIVersion) {
    fun serialize(document: Map<String, Any?>): Map<String, Any?> = document.toMutableMap().apply {
        transform("components") { components ->
            components.mapObject {
                transformEntries("schemas", ::schema)
                transformEntries("parameters", ::parameter)
                transformEntries("headers", ::parameter)
                transformEntries("requestBodies", ::body)
                transformEntries("responses", ::response)
            }
        }
        transformEntries("paths", ::path)
        putIfAbsent("paths", emptyMap<String, Any?>())
    }

    private fun path(value: Any?): Any? = value.mapObject {
        transformList("parameters", ::parameter)
        for (method in listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")) {
            transform(method, ::operation)
        }
    }

    private fun operation(value: Any?): Any? = value.mapObject {
        transformList("parameters", ::parameter)
        transform("requestBody", ::body)
        transformEntries("responses", ::response)
    }

    private fun parameter(value: Any?): Any? = value.mapObject {
        // A parameter's default belongs to its schema in both supported OpenAPI versions.
        remove("default")?.let { default ->
            this["schema"] = (this["schema"] ?: emptyMap<String, Any?>()).mapObject { this["default"] = default }
        }
        transform("schema", ::schema)
        transform("content", ::content)
    }

    private fun body(value: Any?): Any? = value.mapObject {
        transform("content", ::content)
    }

    private fun response(value: Any?): Any? = value.mapObject {
        transformEntries("headers", ::parameter)
        transform("content", ::content)
    }

    private fun content(value: Any?): Any? = value.mapObject {
        keys.toList().forEach { mediaType -> this[mediaType] = mediaType(this[mediaType], mediaType) }
    }

    private fun mediaType(value: Any?, mediaType: String): Any? = value.mapObject {
        transform("schema") { schema -> schema(schema, mediaType) }
        transformEntries("encoding") { encoding ->
            encoding.mapObject { transformEntries("headers", ::parameter) }
        }
    }

    private fun schema(value: Any?): Any? = schema(value, null)

    private fun schema(value: Any?, mediaType: String?): Any? = value.mapObject {
        transformEntries("properties", ::schema)
        transform("items", ::schema)
        transform("additionalProperties", ::schema)
        for (keyword in listOf("allOf", "oneOf", "anyOf")) transformList(keyword, ::schema)
        transform("not", ::schema)

        val nullable = this["nullable"] == true
        if (containsKey("\$ref")) {
            // References are shared by nullable and non-nullable DTO properties.
            remove("nullable")
            if (nullable) {
                val ref = remove("\$ref")
                val nullSchema = if (version == OpenAPIVersion.V3_0) {
                    mapOf("type" to "object", "nullable" to true, "enum" to listOf(null))
                } else {
                    mapOf("type" to "null")
                }
                this["anyOf"] = listOf(mapOf("\$ref" to ref), nullSchema)
            }
        }

        if (version == OpenAPIVersion.V3_0) {
            if (nullable) (this["enum"] as? List<*>)?.let { this["enum"] = it + listOf(null) }
            val examples = remove("examples") as? List<*>
            if (!containsKey("example") && !examples.isNullOrEmpty()) this["example"] = examples.first()
        } else {
            remove("nullable")
            if (nullable && !containsKey("anyOf")) {
                this["type"] = this["type"]?.let { listOf(it, "null") } ?: "null"
                (this["enum"] as? List<*>)?.let { this["enum"] = it + listOf(null) }
            }
            remove("example")?.let { example ->
                val examples = this["examples"] as? List<*> ?: emptyList<Any?>()
                this["examples"] = if (example in examples) examples else examples + example
            }
            when (this["format"]?.toString()) {
                "byte" -> {
                    remove("format")
                    this["contentEncoding"] = "base64"
                }
                "binary" -> {
                    // Raw binary is not a JSON string. The annotation also preserves Swagger UI's file input.
                    remove("format")
                    remove("type")
                    this["contentMediaType"] = mediaType ?: "application/octet-stream"
                }
            }
        }
    }

    private fun Any?.mapObject(transform: MutableMap<String, Any?>.() -> Unit): Any? {
        if (this !is Map<*, *>) return this
        @Suppress("UNCHECKED_CAST")
        return (this as Map<String, Any?>).toMutableMap().apply(transform)
    }

    private fun MutableMap<String, Any?>.transform(key: String, transform: (Any?) -> Any?) {
        if (containsKey(key)) this[key] = transform(this[key])
    }

    private fun MutableMap<String, Any?>.transformEntries(key: String, transform: (Any?) -> Any?) {
        transform(key) { value ->
            value.mapObject { keys.toList().forEach { name -> this[name] = transform(this[name]) } }
        }
    }

    private fun MutableMap<String, Any?>.transformList(key: String, transform: (Any?) -> Any?) {
        transform(key) { value -> (value as? List<*>)?.map(transform) ?: value }
    }
}
