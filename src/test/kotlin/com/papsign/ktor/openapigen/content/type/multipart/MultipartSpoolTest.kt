package com.papsign.ktor.openapigen.content.type.multipart

import com.papsign.ktor.openapigen.annotations.type.file.FileSize
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.support.ProperException
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.test.assertEquals

internal class MultipartSpoolTest {
    @FormDataRequest
    data class PlainRequest(
        val first: NamedFileInputStream,
        val second: NamedFileInputStream?,
    )

    @FormDataRequest
    data class LimitedRequest(
        @FileSize(max = 1024 * 1024) val file: NamedFileInputStream,
    )

    private val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))

    private fun spooledFiles(): Int =
        Files.list(tmpDir).use { files ->
            files.filter { it.name.startsWith("openapigen-multipart-") }.count().toInt()
        }

    private fun content(
        size: Int,
        seed: Int,
    ) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private suspend fun HttpClient.upload(
        path: String,
        vararg files: Pair<String, ByteArray>,
    ): HttpResponse =
        post(path) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        files.forEach { (name, bytes) ->
                            append(
                                name,
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                                },
                            )
                        }
                    },
                ),
            )
        }

    // DELETE_ON_CLOSE unlinks the file right after opening on POSIX, so only leaks are observable.
    private fun spoolTest(
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                route("plain") {
                    post<Unit, String, PlainRequest> { _, body ->
                        respond(body.first.readBytes().sha256() + "|" + (body.second?.readBytes()?.sha256() ?: "none"))
                    }
                }
                route("limited") {
                    post<Unit, String, LimitedRequest> { _, body ->
                        respond(body.file.readBytes().sha256())
                    }
                }
                route("failing") {
                    post<Unit, String, PlainRequest> { _, _ ->
                        throw ProperException("handler failed")
                    }
                }
            }
        }
        val baseline = spooledFiles()
        client.block()
        assertEquals(baseline, spooledFiles(), "Spooled files must be deleted after the request")
    }

    @Test
    fun `small unannotated files are readable in any part position`() =
        spoolTest {
            val first = content(4, 1)
            val second = content(100, 2)
            val response = upload("/plain", "first" to first, "second" to second)
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("${first.sha256()}|${second.sha256()}", response.bodyAsText())
        }

    @Test
    fun `large unannotated files are spooled to disk`() =
        spoolTest {
            val first = content(3 * 1024 * 1024, 3)
            val second = content(400 * 1024, 4)
            val response = upload("/plain", "first" to first, "second" to second)
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("${first.sha256()}|${second.sha256()}", response.bodyAsText())
        }

    @Test
    fun `spooled files are deleted when the handler fails`() =
        spoolTest {
            val response = upload("/failing", "first" to content(1024 * 1024, 5))
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `file size limit applies to spooled files`() =
        spoolTest {
            val exact = content(1024 * 1024, 6)
            val ok = upload("/limited", "file" to exact)
            assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
            assertEquals(exact.sha256(), ok.bodyAsText())

            val tooLarge = upload("/limited", "file" to content(1024 * 1024 + 1, 7))
            assertEquals(HttpStatusCode.BadRequest, tooLarge.status)
        }

    @Test
    fun `unknown parts are ignored`() =
        spoolTest {
            val first = content(10, 8)
            val response = upload("/plain", "unknown" to content(2 * 1024 * 1024, 9), "first" to first)
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("${first.sha256()}|none", response.bodyAsText())
        }
}
