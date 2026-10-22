package com.papsign.ktor.openapigen.spec

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.BaseApplicationEngine

/** Engine that loads application modules without opening sockets; used for standalone OpenAPI generation. */
internal class NoopApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
) : BaseApplicationEngine(environment, monitor, developmentMode) {
    override fun start(
        wait: Boolean,
    ): NoopApplicationEngine {
        resolvedConnectorsDeferred.complete(emptyList())
        return this
    }

    override fun stop(
        gracePeriodMillis: Long,
        timeoutMillis: Long,
    ) = Unit

    object Factory : ApplicationEngineFactory<NoopApplicationEngine, Configuration> {
        override fun configuration(
            configure: Configuration.() -> Unit,
        ): Configuration = Configuration().apply(configure)

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: Configuration,
            applicationProvider: () -> Application,
        ): NoopApplicationEngine = NoopApplicationEngine(environment, monitor, developmentMode)
    }
}
