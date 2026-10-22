package com.papsign.ktor.openapigen

import com.papsign.ktor.openapigen.model.DataModel
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val Application.openAPIGen: OpenAPIGen get() = this.plugin(OpenAPIGen)

internal fun Any.classLogger(): Logger = LoggerFactory.getLogger(this::class.java)

internal inline fun <reified T> classLogger(): Logger = LoggerFactory.getLogger(T::class.java)

fun Map<String, *>.cleanEmptyValues(
    serializationSettings: SerializationSettings = SerializationSettings(),
): Map<String, *> =
    filterValues {
        when (it) {
            is Map<*, *> -> it.isNotEmpty() || serializationSettings.skipEmptyMap
            is Collection<*> -> it.isNotEmpty() || serializationSettings.skipEmptyList
            else -> it != null || serializationSettings.skipEmptyValue
        }
    }

fun convertToValue(
    value: Any?,
    serializationSettings: SerializationSettings = SerializationSettings(),
): Any? =
    when (value) {
        is DataModel -> {
            value.serialize()
        }

        is Map<*, *> -> {
            value.entries
                .associate { (key, value) -> Pair(key.toString(), convertToValue(value, serializationSettings)) }
                .cleanEmptyValues(serializationSettings)
        }

        is Iterable<*> -> {
            value.map { convertToValue(it, serializationSettings) }
        }

        else -> {
            value
        }
    }
