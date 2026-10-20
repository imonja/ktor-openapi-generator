package com.papsign.ktor.openapigen.route

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import java.io.Closeable

/** Resources created while parsing a request, released by [OpenAPIRoute] once the handler has finished. */
internal class CallResources : Closeable {
    private val resources = mutableListOf<Closeable>()

    fun register(resource: Closeable) {
        synchronized(resources) { resources += resource }
    }

    override fun close() {
        val released = synchronized(resources) { resources.toList().also { resources.clear() } }
        released.asReversed().forEach { runCatching { it.close() } }
    }

    companion object {
        private val key = AttributeKey<CallResources>("OpenAPIGenCallResources")

        fun of(call: ApplicationCall): CallResources = call.attributes.computeIfAbsent(key) { CallResources() }

        fun release(call: ApplicationCall) {
            call.attributes.getOrNull(key)?.close()
        }
    }
}
