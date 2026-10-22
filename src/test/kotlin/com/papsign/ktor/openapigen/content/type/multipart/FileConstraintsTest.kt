package com.papsign.ktor.openapigen.content.type.multipart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.OpenAPIVersion
import com.papsign.ktor.openapigen.annotations.type.file.AllowedContentTypes
import com.papsign.ktor.openapigen.annotations.type.file.FileSize
import com.papsign.ktor.openapigen.annotations.type.file.FileSizeProcessor
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import com.papsign.ktor.openapigen.support.installJackson
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.InputStream
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FileConstraintsTest {
    private val mapper = ObjectMapper()

    @FormDataRequest
    data class AvatarRequest(
        @FileSize(max = 8)
        @AllowedContentTypes("image/png", "image/jpeg")
        val file: NamedFileInputStream,
        val title: String?,
    )

    @FormDataRequest
    data class WildcardRequest(
        @AllowedContentTypes("image/*")
        val file: NamedFileInputStream?,
    )

    @FormDataRequest
    data class TwoFilesRequest(
        @FileSize(max = 8) val first: NamedFileInputStream,
        @FileSize(min = 0, max = 8) val second: InputStream,
    )

    @FormDataRequest
    data class SpecRequest(
        @FileSize(max = 1024 * 1024)
        @AllowedContentTypes("image/png", "image/webp")
        val avatar: NamedFileInputStream,
        @FileSize(min = 10, max = 2048)
        @AllowedContentTypes("application/pdf")
        val document: ContentInputStream?,
        @PartEncoding("text/plain")
        val notes: InputStream?,
    )

    @FormDataRequest
    data class SizeOnString(
        @FileSize(max = 1) val text: String,
    )

    @FormDataRequest
    data class TypesOnPlainStream(
        @AllowedContentTypes("image/png") val file: InputStream,
    )

    @FormDataRequest
    data class TypesWithEncoding(
        @AllowedContentTypes("image/png")
        @PartEncoding("image/png")
        val file: NamedFileInputStream,
    )

    @FormDataRequest
    data class InvertedSize(
        @FileSize(min = 10, max = 1) val file: NamedFileInputStream,
    )

    data class Uploaded(
        val name: String?,
        val contentType: String?,
        val content: String,
    )

    private fun NamedFileInputStream.uploaded() = Uploaded(name, contentType?.toString(), use { String(it.readBytes()) })

    private suspend fun HttpClient.upload(
        path: String,
        vararg files: Triple<String, String?, ByteArray>,
    ): HttpResponse =
        post(path) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        files.forEach { (name, contentType, bytes) ->
                            append(
                                name,
                                bytes,
                                Headers.build {
                                    contentType?.let { append(HttpHeaders.ContentType, it) }
                                    append(HttpHeaders.ContentDisposition, "filename=\"$name.bin\"")
                                },
                            )
                        }
                    },
                ),
            )
        }

    private fun runtimeTest(
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                route("avatar") {
                    post<Unit, Uploaded, AvatarRequest> { _, body -> respond(body.file.uploaded()) }
                }
                route("wildcard") {
                    post<Unit, Uploaded, WildcardRequest> { _, body ->
                        respond(body.file?.uploaded() ?: Uploaded(null, null, "none"))
                    }
                }
                route("two-files") {
                    post<Unit, String, TwoFilesRequest> { _, body ->
                        respond(String(body.first.readBytes()) + "|" + String(body.second.readBytes()))
                    }
                }
            }
        }
        client.block()
    }

    @Test
    fun `valid file is passed to the handler with its name and content type`() =
        runtimeTest {
            val response = upload("/avatar", Triple("file", "image/png; foo=bar", "12345678".toByteArray()))
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val body = mapper.readTree(response.bodyAsText())
            assertEquals("file.bin", body["name"].asText())
            assertEquals("image/png; foo=bar", body["contentType"].asText())
            assertEquals("12345678", body["content"].asText())
        }

    @Test
    fun `file larger than max is rejected`() =
        runtimeTest {
            val response = upload("/avatar", Triple("file", "image/png", "123456789".toByteArray()))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "at most 8 bytes")
        }

    @Test
    fun `empty file is rejected by default`() =
        runtimeTest {
            val response = upload("/avatar", Triple("file", "image/png", ByteArray(0)))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "at least 1 bytes, but it is 0")
        }

    @Test
    fun `disallowed content type is rejected`() =
        runtimeTest {
            val response = upload("/avatar", Triple("file", "image/gif", "GIF".toByteArray()))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "should be one of image/png, image/jpeg, but it is image/gif")
        }

    @Test
    fun `missing content type is rejected`() =
        runtimeTest {
            val response = upload("/avatar", Triple("file", null, "1".toByteArray()))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "but it is missing")
        }

    @Test
    fun `wildcard content type matches any subtype and absent optional file is null`() =
        runtimeTest {
            val webp = upload("/wildcard", Triple("file", "IMAGE/WEBP", "webp".toByteArray()))
            assertEquals(HttpStatusCode.OK, webp.status, webp.bodyAsText())
            assertEquals("webp", mapper.readTree(webp.bodyAsText())["content"].asText())

            assertEquals(HttpStatusCode.BadRequest, upload("/wildcard", Triple("file", "text/plain", "x".toByteArray())).status)

            val none = upload("/wildcard", Triple("other", "text/plain", "x".toByteArray()))
            assertEquals("none", mapper.readTree(none.bodyAsText())["content"].asText())
        }

    @Test
    fun `annotated files stay readable when they are not the last part`() =
        runtimeTest {
            val response = upload(
                "/two-files",
                Triple("first", "image/png", "first".toByteArray()),
                Triple("second", "image/png", ByteArray(0)),
            )
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("first|", response.bodyAsText())
        }

    @Test
    fun `malformed multipart body is a bad request`() =
        runtimeTest {
            val response = post("/avatar") {
                header(
                    HttpHeaders.ContentType,
                    ContentType.MultiPart.FormData
                        .withParameter("boundary", "xyz")
                        .toString(),
                )
                setBody("--xyz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\ntruncated")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    private fun registrationError(
        register: NormalOpenAPIRoute.() -> Unit,
    ): String {
        val error = assertFails {
            testApplication {
                application {
                    setupBaseTestServer()
                    apiRouting { register() }
                }
                startApplication()
            }
        }
        return generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
    }

    @Test
    fun `misplaced file annotations fail route registration`() {
        assertContains(
            registrationError { post<Unit, String, SizeOnString> { _, _ -> respond("") } },
            "SizeOnString.text: FileSize can only be used on types",
        )
        assertContains(
            registrationError { post<Unit, String, TypesOnPlainStream> { _, _ -> respond("") } },
            "TypesOnPlainStream.file: AllowedContentTypes can only be used on types",
        )
        assertContains(
            registrationError { post<Unit, String, TypesWithEncoding> { _, _ -> respond("") } },
            "TypesWithEncoding.file: PartEncoding and AllowedContentTypes cannot be combined",
        )
        assertContains(
            registrationError { post<Unit, String, InvertedSize> { _, _ -> respond("") } },
            "InvertedSize.file: FileSize requires 0 <= min <= max",
        )
    }

    @ParameterizedTest
    @EnumSource(OpenAPIVersion::class)
    fun `constraints are documented in the spec`(
        version: OpenAPIVersion,
    ) = testApplication {
        application {
            installJackson()
            install(OpenAPIGen) {
                openApiVersion = version.value
                replaceModule(
                    DefaultSchemaNamer,
                    object : SchemaNamer {
                        override fun get(
                            type: KType,
                        ): String = type.jvmErasure.simpleName!!
                    },
                )
            }
            apiRouting {
                route("upload") {
                    post<Unit, String, SpecRequest> { _, _ -> respond("") }
                }
            }
        }
        val document = mapper.readTree(client.get("/openapi.json").bodyAsText())
        assertValidDocument(document, version)

        val encoding = document["paths"]["/upload"]["post"]["requestBody"]["content"]["multipart/form-data"]["encoding"]
        assertEquals("image/png, image/webp", encoding["avatar"]["contentType"].asText())
        assertEquals("application/pdf", encoding["document"]["contentType"].asText())
        assertEquals("text/plain", encoding["notes"]["contentType"].asText())

        val properties = document["components"]["schemas"]["SpecRequest"]["properties"]
        val avatar = properties["avatar"]
        assertEquals(1048576, avatar["x-max-file-size"].asLong())
        assertFalse(avatar.has("x-min-file-size"))
        assertFalse(avatar.has("maxLength"))
        assertEquals("Max file size: 1 MB", avatar["description"].asText())
        val documentPart = properties["document"]
        assertEquals(10, documentPart["x-min-file-size"].asLong())
        assertEquals(2048, documentPart["x-max-file-size"].asLong())
        assertEquals("File size: 10 bytes to 2 KB", documentPart["description"].asText())

        if (version == OpenAPIVersion.V3_0) {
            assertEquals("binary", avatar["format"].asText())
            assertFalse(documentPart.has("contentMediaType"))
        } else {
            // Multiple types cannot be expressed by contentMediaType, the part encoding carries them.
            assertEquals("application/octet-stream", avatar["contentMediaType"].asText())
            assertEquals("application/pdf", documentPart["contentMediaType"].asText())
        }
    }

    @Test
    fun `file sizes are formatted for humans`() {
        assertEquals("1 byte", FileSizeProcessor.formatBytes(1))
        assertEquals("1500 bytes", FileSizeProcessor.formatBytes(1500))
        assertEquals("3 KB", FileSizeProcessor.formatBytes(3 * 1024))
        assertEquals("5 MB", FileSizeProcessor.formatBytes(5 * 1024 * 1024))
    }

    private fun assertValidDocument(
        document: JsonNode,
        version: OpenAPIVersion,
    ) {
        val dialect = if (version == OpenAPIVersion.V3_0) SpecificationVersion.DRAFT_4 else SpecificationVersion.DRAFT_2020_12
        val resource = "/openapi/${version.shortValue}.schema.json"
        val schema = javaClass.getResourceAsStream(resource)!!.use { SchemaRegistry.withDefaultDialect(dialect).getSchema(it) }
        val errors = schema.validate(document)
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }
}
