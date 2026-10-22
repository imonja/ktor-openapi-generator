@file:JvmName("GenerateSpecMain")

package com.papsign.ktor.openapigen.spec

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Entry point used by the Gradle plugin: `--module <fqName> --output <path>`.
 *
 * Exits the JVM explicitly so that non-daemon threads started by application code cannot keep it alive.
 */
fun main(
    args: Array<String>,
) {
    val status = try {
        val options = parseArgs(args)
        OpenAPISpecGenerator.generateTo(Path.of(options.getValue("--output")), options.getValue("--module"))
        0
    } catch (e: Throwable) {
        e.printStackTrace()
        1
    }
    System.out.flush()
    System.err.flush()
    exitProcess(status)
}

private fun parseArgs(
    args: Array<String>,
): Map<String, String> {
    val known = setOf("--module", "--output")
    val options = args.toList().chunked(2).associate { pair ->
        require(pair.size == 2 && pair[0] in known) { "Usage: GenerateSpecMain --module <fqName> --output <path>" }
        pair[0] to pair[1]
    }
    require(options.keys == known) { "Usage: GenerateSpecMain --module <fqName> --output <path>" }
    return options
}
