package io.github.imonja.ktor.openapi.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryVersionCheckTest {

    @Test
    fun `no warning when versions match or library is not a module`() {
        assertNull(LibraryVersionCheck.mismatchWarning("0.4.0", "0.4.0"))
        assertNull(LibraryVersionCheck.mismatchWarning(null, "0.4.0"))
    }

    @Test
    fun `warning names both versions`() {
        val warning = LibraryVersionCheck.mismatchWarning("0.5.0", "0.4.0")!!

        assertTrue(warning.contains("ktor-openapi-generator 0.5.0"), warning)
        assertTrue(warning.contains("plugin version is 0.4.0"), warning)
    }

    @Test
    fun `missing runner message depends on whether the library is present`() {
        assertTrue(LibraryVersionCheck.missingRunnerMessage(null, "0.4.0").contains("is not on the runtime classpath"))
        assertTrue(LibraryVersionCheck.missingRunnerMessage("0.3.3", "0.4.0").contains("0.3.3 does not support"))
    }

    @Test
    fun `runner is found in jars and class directories`(@TempDir dir: File) {
        val runnerPath = "com/papsign/ktor/openapigen/spec/GenerateSpecMain.class"
        val jar = File(dir, "library.jar")
        ZipOutputStream(jar.outputStream()).use { it.putNextEntry(ZipEntry(runnerPath)) }
        val classes = File(dir, "classes").apply { File(this, runnerPath).apply { parentFile.mkdirs() }.writeText("") }
        val other = File(dir, "other.jar")
        ZipOutputStream(other.outputStream()).use { it.putNextEntry(ZipEntry("Other.class")) }

        assertTrue(LibraryVersionCheck.runnerOnClasspath(listOf(other, jar)))
        assertTrue(LibraryVersionCheck.runnerOnClasspath(listOf(classes)))
        assertFalse(LibraryVersionCheck.runnerOnClasspath(listOf(other, File(dir, "missing"))))
        assertEquals(false, LibraryVersionCheck.runnerOnClasspath(emptyList()))
    }
}
