@file:Suppress("UNCHECKED_CAST")

package com.papsign.ktor.openapigen

import com.papsign.ktor.openapigen.model.schema.SchemaModel
import kotlin.reflect.KType

typealias SchemaMap = Map<KType, SchemaModel<*>>

typealias MutableSchemaMap = MutableMap<KType, SchemaModel<*>>

typealias LinkedHashSchemaMap = LinkedHashMap<KType, SchemaModel<*>>
typealias HashSchemaMap = HashMap<KType, SchemaModel<*>>

inline fun <reified T> SchemaMap.get(): SchemaModel<T>? = get(getKType<T>()) as SchemaModel<T>?

inline fun <reified T> SchemaMap.containsKey(): Boolean = containsKey(getKType<T>())

inline fun <reified T> MutableSchemaMap.put(
    value: SchemaModel<T>,
): SchemaModel<T>? = put(getKType<T>(), value) as SchemaModel<T>?

inline fun <reified T> MutableSchemaMap.remove(): SchemaModel<T>? = remove(getKType<T>()) as SchemaModel<T>?
