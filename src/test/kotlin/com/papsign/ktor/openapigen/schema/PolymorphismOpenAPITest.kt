package com.papsign.ktor.openapigen.schema

import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.support.setupBaseTestServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val ref = "\$ref"

internal class PolymorphismOpenAPITest {
    @Test
    fun willDiscriminatorsBePresent() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                sealedRoute()
            }
        }

        client.get("http://localhost/openapi.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            val bodyAsText = bodyAsText()
            assertTrue(
                bodyAsText.contains(
                    """"Base" : {
        "title" : "Base",
        "discriminator" : {
          "propertyName" : "type"
        },
        "oneOf" : [ {
          "$ref" : "#/components/schemas/A"
        }, {
          "$ref" : "#/components/schemas/B"
        }, {
          "$ref" : "#/components/schemas/C"
        } ],
        "properties" : {
          "type" : {
            "type" : "string"
          }
        }"""
                )
            )
            assertTrue(
                bodyAsText.contains(
                    """"A" : {
        "title" : "A",
        "properties" : {
          "str" : {
            "type" : "string"
          },
          "type" : {
            "type" : "string"
          }
        },
        "required" : [ "str" ],
        "type" : "object"
      }"""
                )
            )
        }
    }
}
