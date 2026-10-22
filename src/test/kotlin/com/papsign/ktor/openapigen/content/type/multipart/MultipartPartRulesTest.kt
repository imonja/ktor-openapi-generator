package com.papsign.ktor.openapigen.content.type.multipart

import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormBuilder
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
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals

internal class MultipartPartRulesTest {
    @FormDataRequest
    data class UploadRequest(
        val file: NamedFileInputStream,
        val preview: NamedFileInputStream?,
        val count: Int,
        val title: String?,
        val retries: Int = 5,
        val at: Instant?,
    )

    private fun FormBuilder.file(
        name: String,
        bytes: ByteArray = "data".toByteArray(),
    ) = append(name, bytes, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$name.bin\"") })

    private suspend fun HttpClient.upload(
        build: FormBuilder.() -> Unit,
    ): HttpResponse =
        post("/upload") {
            setBody(MultiPartFormDataContent(formData(build)))
        }

    private fun partsTest(
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                route("upload") {
                    post<Unit, String, UploadRequest> { _, body ->
                        respond(
                            listOf(
                                String(body.file.readBytes()),
                                body.preview?.let { String(it.readBytes()) },
                                body.count,
                                body.title,
                                body.retries,
                                body.at,
                            ).joinToString("|"),
                        )
                    }
                }
            }
        }
        client.block()
    }

    @Test
    fun `absent optional parts use the Kotlin default or null`() =
        partsTest {
            val response = upload {
                file("file")
                append("count", "3")
            }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("data|null|3|null|5|null", response.bodyAsText())
        }

    @Test
    fun `present parts override defaults`() =
        partsTest {
            val response = upload {
                file("file")
                file("preview", "small".toByteArray())
                append("count", "3")
                append("title", "hello")
                append("retries", "1")
                append("at", "2026-10-20T00:00:00Z")
            }
            assertEquals("data|small|3|hello|1|2026-10-20T00:00:00Z", response.bodyAsText())
        }

    @Test
    fun `missing required file part is a required field error`() =
        partsTest {
            val response = upload { append("count", "3") }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "The field file is required")
        }

    @Test
    fun `missing required scalar part is a required field error`() =
        partsTest {
            val response = upload { file("file") }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "The field count is required")
        }

    @Test
    fun `text part in place of a file is a bad request`() =
        partsTest {
            val response = upload {
                append("file", "not a file")
                append("count", "3")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "The field file must be a file part")
        }

    @Test
    fun `empty text part for an optional file means no file`() =
        partsTest {
            val empty = upload {
                file("file")
                append("preview", "")
                append("count", "3")
            }
            assertEquals("data|null|3|null|5|null", empty.bodyAsText())

            val text = upload {
                file("file")
                append("preview", "text")
                append("count", "3")
            }
            assertEquals(HttpStatusCode.BadRequest, text.status)
            assertContains(text.bodyAsText(), "The field preview must be a file part")
        }

    @Test
    fun `file part in place of a scalar is a bad request`() =
        partsTest {
            val response = upload {
                file("file")
                file("count")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "The field count must be a text part")
        }

    @Test
    fun `unparsable values are a bad request`() =
        partsTest {
            val number = upload {
                file("file")
                append("count", "abc")
            }
            assertEquals(HttpStatusCode.BadRequest, number.status)
            assertContains(number.bodyAsText(), "The field count has an invalid value")

            val instant = upload {
                file("file")
                append("count", "3")
                append("at", "yesterday")
            }
            assertEquals(HttpStatusCode.BadRequest, instant.status)
            assertContains(instant.bodyAsText(), "The field at has an invalid value")
        }

    @Test
    fun `parts above the form field limit are a bad request`() {
        val property = "io.ktor.server.request.formFieldLimit"
        System.setProperty(property, "1000")
        try {
            partsTest {
                val file = upload {
                    file("file", ByteArray(5000))
                    append("count", "3")
                }
                assertEquals(HttpStatusCode.BadRequest, file.status, file.bodyAsText())
                assertContains(file.bodyAsText(), "exceeds limit")

                val text = upload {
                    append("title", "x".repeat(5000))
                    file("file")
                    append("count", "3")
                }
                assertEquals(HttpStatusCode.BadRequest, text.status, text.bodyAsText())
            }
        } finally {
            System.clearProperty(property)
        }
    }
}
