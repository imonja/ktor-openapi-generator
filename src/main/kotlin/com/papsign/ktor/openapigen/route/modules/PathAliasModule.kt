package com.papsign.ktor.openapigen.route.modules

import com.papsign.ktor.openapigen.modules.OpenAPIModule

/** Operation IDs already assigned within one group of path aliases, including nested groups. */
@PublishedApi
internal class PathAliasGroup {
    private val indicesById = mutableMapOf<String, MutableSet<Int>>()

    /** Suffixes [operationId] with [index] when an earlier alias in the group already uses it. */
    @Synchronized
    fun operationId(
        operationId: String,
        index: Int,
    ): String {
        val indices = indicesById.getOrPut(operationId) { mutableSetOf() }
        val duplicate = indices.any { it < index }
        indices.add(index)
        return if (duplicate) "${operationId}_$index" else operationId
    }
}

/** Marks a route as the alias at [index] of [size] paths declared by `routes()` or `@Paths`. */
@PublishedApi
internal class PathAliasModule(
    val group: PathAliasGroup,
    val index: Int,
    val size: Int,
) : OpenAPIModule
