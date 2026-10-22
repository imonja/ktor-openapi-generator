package com.papsign.ktor.openapigen.content.type.binary

import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.support.installOpenAPI
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.InputStream
import kotlin.random.Random

const val CONTENT_TYPE = "image/png"

class BinaryContentTypeParserTest {

    @BinaryRequest([CONTENT_TYPE])
    @BinaryResponse([CONTENT_TYPE])
    data class Stream(val stream: InputStream)

    @Test
    fun `Missing accept`() = testApplication {
        val route = "/test"
        val bytes = Random.nextBytes(20)
        application(setupTestApplication(bytes))

        client.post(route) {
            headers {
                append(HttpHeaders.ContentType, CONTENT_TYPE)
            }
            setBody(bytes)
        }.let {
            Assertions.assertEquals(ContentType.parse(CONTENT_TYPE), it.contentType())
            Assertions.assertArrayEquals(bytes, it.readRawBytes())
        }
    }

    @Test
    fun `Missing Content-Type`() = testApplication {
        val route = "/test"
        val bytes = Random.nextBytes(20)
        application(setupTestApplication(bytes))

        client.post(route) {
            headers {
                append(HttpHeaders.Accept, CONTENT_TYPE)
            }
            setBody(bytes)
        }.let {
            Assertions.assertEquals(HttpStatusCode.UnsupportedMediaType, it.status)
        }
    }

    @Test
    fun `Bad Accept`() = testApplication {
        val route = "/test"
        val bytes = Random.nextBytes(20)
        application(setupTestApplication(bytes))

        client.post(route) {
            headers {
                append(HttpHeaders.ContentType, CONTENT_TYPE)
                append(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            setBody(bytes)
        }.let {
            Assertions.assertEquals(HttpStatusCode.NotAcceptable, it.status)
        }
    }

    @Test
    fun `Bad Content-Type`() = testApplication {
        val route = "/test"
        val bytes = Random.nextBytes(20)
        application(setupTestApplication(bytes))

        client.post(route) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                append(HttpHeaders.Accept, CONTENT_TYPE)
            }
            setBody(bytes)
        }.let {
            Assertions.assertEquals(HttpStatusCode.UnsupportedMediaType, it.status)
        }
    }

    @Test
    fun `Forbidden route`() = testApplication {
        val route = "/forbidden"
        val bytes = Random.nextBytes(20)
        application(setupTestApplication(bytes))

        client.post(route) {
            headers {
                append(HttpHeaders.ContentType, CONTENT_TYPE)
                append(HttpHeaders.Accept, CONTENT_TYPE)
                setBody(bytes)
            }
            setBody(bytes)
        }.let {
            Assertions.assertEquals(HttpStatusCode.Forbidden, it.status)
        }
    }

    private fun setupTestApplication(expectedOutput: ByteArray): Application.() -> Unit = {
        installOpenAPI()
        apiRouting {
            // (this.ktorRoute as Routing).trace { println(it.buildText()) }
            route("test") {
                post<Unit, Stream, Stream> { _, body ->
                    val actual = body.stream.readBytes()
                    Assertions.assertArrayEquals(expectedOutput, actual)
                    respond(Stream(actual.inputStream()))
                }
            }
            route("forbidden") {
                post<Unit, Stream, Stream> { _, _ ->
                    respondWithStatus(HttpStatusCode.Forbidden)
                }
            }
        }
    }
}
