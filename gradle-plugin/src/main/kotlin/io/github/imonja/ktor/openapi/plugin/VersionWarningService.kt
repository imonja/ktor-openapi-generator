package io.github.imonja.ktor.openapi.plugin

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ConcurrentHashMap

/** Lets every library version warning be logged once per build, however many documents are generated. */
abstract class VersionWarningService : BuildService<BuildServiceParameters.None> {
    private val logged = ConcurrentHashMap.newKeySet<String>()

    fun firstTime(
        warning: String,
    ): Boolean = logged.add(warning)

    companion object {
        const val NAME = "ktorOpenApiVersionWarnings"
    }
}
