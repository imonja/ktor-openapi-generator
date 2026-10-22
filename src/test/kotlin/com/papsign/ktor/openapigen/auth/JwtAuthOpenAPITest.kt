package com.papsign.ktor.openapigen.auth

import com.papsign.ktor.openapigen.auth.JwtTestApplication.testServerWithJwtAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class JwtAuthOpenAPITest {
    @Test
    fun testRequest() =
        testApplication {
            application {
                testServerWithJwtAuth()
            }
            client.get("http://localhost/openapi.json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val bodyAsText = bodyAsText()
                assertTrue(
                    bodyAsText.contains(
                        """"securitySchemes" : {
      "ThisIsSchemeName" : {
        "in" : "cookie",
        "name" : "ThisIsCookieName",
        "type" : "apiKey"
      },
      "jwtAuth" : {
        "bearerFormat" : "JWT",
        "scheme" : "bearer",
        "type" : "http"
      }
    }""",
                    ),
                )
                assertTrue(
                    bodyAsText.contains(
                        """"security" : [ {
          "jwtAuth" : [ ],
          "ThisIsSchemeName" : [ ]
        }""",
                    ),
                )
            }
        }
}
