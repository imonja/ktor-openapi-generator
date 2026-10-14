package com.papsign.ktor.openapigen.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.OpenAPIVersion
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.model.base.OpenAPIModel
import com.papsign.ktor.openapigen.model.operation.MediaTypeModel
import com.papsign.ktor.openapigen.model.operation.ParameterLocation
import com.papsign.ktor.openapigen.model.operation.ParameterModel
import com.papsign.ktor.openapigen.model.operation.RequestBodyModel
import com.papsign.ktor.openapigen.model.operation.StatusResponseModel
import com.papsign.ktor.openapigen.model.schema.DataFormat
import com.papsign.ktor.openapigen.model.schema.DataType
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import com.papsign.ktor.openapigen.support.installJackson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.InputStream
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class OpenAPIVersionOpenAPITest {
    private val mapper = ObjectMapper()

    enum class Choice { FIRST, SECOND }

    data class Child(val id: String, val parent: Child?)

    data class Payload(
        val requiredChild: Child,
        val child: Child?,
        @Deprecated("Use child") val legacyChild: Child?,
        val text: String?,
        val choice: Choice?,
        val children: List<Child?>?,
        val scores: Map<String, Int?>?,
        val bytes: ByteArray?,
        val file: InputStream?,
        val nothing: Nothing? = null
    )

    data class Parameters(
        @PathParam("Identifier") val id: String,
        @QueryParam("Optional filter") val filter: String?,
        @QueryParam("Page size") val limit: Int = 10
    )

    @Test
    fun `default document and plugin configuration use 3_2_1`() {
        val api = OpenAPIModel()
        assertEquals("3.2.1", api.serialize()["openapi"])
        assertEquals("3.2.1", OpenAPIGen.Configuration(api).openApiVersion)
        assertValidDocument(mapper.valueToTree(api.serialize()), OpenAPIVersion.V3_2)
    }

    @ParameterizedTest
    @ValueSource(strings = ["3.0.0", "3.0.1", "3.0.2", "3.0.3", "3.0.4", "3.2.0", "3.2.1"])
    fun `endpoint preserves the exact version and serves a valid document in its format`(version: String): Unit = testApplication {
        val format = OpenAPIVersion.fromValue(version)
        application {
            installJackson()
            install(OpenAPIGen) {
                openApiVersion = version
                replaceModule(
                    DefaultSchemaNamer,
                    object : SchemaNamer {
                        override fun get(type: KType): String = type.jvmErasure.simpleName!!
                    }
                )
            }
            apiRouting {
                route("/models/{id}") {
                    get<Parameters, Payload> { _ -> error("Documentation only") }
                }
            }
        }

        val response = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)
        val document = mapper.readTree(response.bodyAsText())
        assertEquals(version, document["openapi"].asText())
        assertValidDocument(document, format)

        val schemas = document["components"]["schemas"]
        val properties = schemas["Payload"]["properties"]
        assertEquals("#/components/schemas/Child", properties["requiredChild"]["\$ref"].asText())
        assertEquals("#/components/schemas/Child", properties["child"]["anyOf"][0]["\$ref"].asText())
        assertEquals(true, properties["legacyChild"]["deprecated"].asBoolean())
        if (format == OpenAPIVersion.V3_2) {
            // 2020-12 allows keywords next to the reference, so the 3.0 allOf wrapper is inlined.
            assertTrue(properties["legacyChild"].has("anyOf"))
            assertFalse(properties["legacyChild"].has("allOf"))
        } else {
            assertTrue(properties["legacyChild"]["allOf"][0].has("anyOf"))
        }
        assertEquals("Payload", schemas["Payload"]["title"].asText())
        assertEquals("Child", schemas["Child"]["title"].asText())
        assertTrue(schemas["Child"]["properties"]["parent"].has("anyOf"))

        val operation = document["paths"]["/models/{id}"]["get"]
        val querySchema = operation["parameters"].first { it["name"].asText() == "filter" }["schema"]
        val limit = operation["parameters"].first { it["name"].asText() == "limit" }
        assertEquals(10, limit["schema"]["default"].asInt())
        assertFalse(limit.has("default"))
        if (format == OpenAPIVersion.V3_2) {
            assertEquals(mapper.readTree("[\"string\",\"null\"]"), properties["text"]["type"])
            assertFalse(properties["text"].has("nullable"))
            assertEquals(mapper.readTree("[\"string\",\"null\"]"), properties["choice"]["type"])
            assertTrue(properties["choice"]["enum"].any { it.isNull })
            assertEquals(mapper.readTree("[\"array\",\"null\"]"), properties["children"]["type"])
            assertTrue(properties["children"]["items"].has("anyOf"))
            assertEquals(mapper.readTree("[\"integer\",\"null\"]"), properties["scores"]["additionalProperties"]["type"])
            assertEquals("base64", properties["bytes"]["contentEncoding"].asText())
            assertEquals("application/octet-stream", properties["file"]["contentMediaType"].asText())
            assertFalse(properties["file"].has("type"))
            assertEquals("null", properties["nothing"]["type"].asText())
            assertEquals("string", querySchema["type"].asText())
            assertPayloadSemantics(document)
        } else {
            assertEquals("string", properties["text"]["type"].asText())
            assertEquals(true, properties["text"]["nullable"].asBoolean())
            assertEquals("byte", properties["bytes"]["format"].asText())
            assertEquals("binary", properties["file"]["format"].asText())
            assertEquals(false, querySchema["nullable"].asBoolean())
        }

        val ui = client.get("/swagger-ui/index.html")
        assertEquals(HttpStatusCode.OK, ui.status)
        val bundle = client.get("/swagger-ui/swagger-ui-bundle.js")
        assertEquals(HttpStatusCode.OK, bundle.status)
        assertTrue(bundle.bodyAsText().contains("oas32"), "Bundled Swagger UI must include OpenAPI 3.2 support")
    }

    @ParameterizedTest
    @EnumSource(OpenAPIVersion::class)
    fun `component and media type schemas migrate without modifying example payloads`(version: OpenAPIVersion) {
        val example = mapOf("nullable" to true, "type" to "string", "schema" to mapOf("format" to "binary"))
        val schema = SchemaModel.SchemaModelLitteral<Map<String, Any>>(
            type = DataType.`object`,
            nullable = true,
            example = example,
            examples = listOf(example)
        )
        val api = OpenAPIModel(openapi = version.value)
        api.components.schemas["Example"] = schema
        api.components.parameters["Filter"] = ParameterModel("filter", ParameterLocation.query, schema = schema)
        api.components.requestBodies["Body"] = RequestBodyModel(content = mutableMapOf("application/json" to MediaTypeModel(schema, example)))
        api.components.responses["Result"] = StatusResponseModel("Success", content = mutableMapOf("application/json" to MediaTypeModel(schema)))
        api.components.schemas["RawFile"] = SchemaModel.SchemaModelLitteral<InputStream>(DataType.string, DataFormat.binary)
        api.components.requestBodies["Binary"] = RequestBodyModel(
            content = mutableMapOf("image/png" to MediaTypeModel(api.components.schemas.getValue("RawFile")))
        )

        val document = mapper.valueToTree<JsonNode>(api.serialize())
        assertValidDocument(document, version)
        val component = document["components"]["schemas"]["Example"]
        assertEquals("Example", component["title"].asText())
        // Only component schemas are titled; the same model used inline is otherwise identical.
        val serialized = (component as ObjectNode).deepCopy().apply { remove("title") }
        val serializedExample = if (version == OpenAPIVersion.V3_2) serialized["examples"][0] else serialized["example"]
        assertEquals(mapper.valueToTree(example), serializedExample)
        assertEquals(mapper.valueToTree(example), document["components"]["requestBodies"]["Body"]["content"]["application/json"]["example"])
        assertEquals(serialized, document["components"]["parameters"]["Filter"]["schema"])
        assertEquals(serialized, document["components"]["requestBodies"]["Body"]["content"]["application/json"]["schema"])
        assertEquals(serialized, document["components"]["responses"]["Result"]["content"]["application/json"]["schema"])
        if (version == OpenAPIVersion.V3_2) {
            assertEquals("application/octet-stream", document["components"]["schemas"]["RawFile"]["contentMediaType"].asText())
            val binary = document["components"]["requestBodies"]["Binary"]["content"]["image/png"]["schema"]
            assertEquals("image/png", binary["contentMediaType"].asText())
            assertFalse(binary.has("type"))
        }
        // Serializing another version must not mutate the shared source models.
        assertEquals(example, schema.example)
        assertEquals(true, schema.nullable)
    }

    @ParameterizedTest
    @EnumSource(OpenAPIVersion::class)
    fun `component schemas are titled with their component name`(version: OpenAPIVersion) {
        val api = OpenAPIModel(openapi = version.value)
        api.components.schemas["Color"] = SchemaModel.SchemaModelEnum<String>(listOf("RED", "GREEN"))
        api.components.schemas["Named"] = SchemaModel.SchemaModelObj<Any?>(
            mapOf("color" to SchemaModel.SchemaModelRef<Any?>("#/components/schemas/Color")),
            listOf("color")
        )
        api.components.schemas["Custom"] = SchemaModel.SchemaModelLitteral<Map<String, Any>>(
            type = DataType.`object`,
            example = mapOf("title" to "Explicit")
        )

        val document = mapper.valueToTree<JsonNode>(api.serialize())
        assertValidDocument(document, version)
        val schemas = document["components"]["schemas"]
        assertEquals("Color", schemas["Color"]["title"].asText())
        assertEquals("Named", schemas["Named"]["title"].asText())
        assertEquals("title", schemas["Named"].fieldNames().next(), "title should lead the component schema")
        assertFalse(schemas["Named"]["properties"]["color"].has("title"), "inline schemas stay untitled")
        assertEquals("Custom", schemas["Custom"]["title"].asText(), "example payloads are not schema titles")
    }

    @ParameterizedTest
    @EnumSource(OpenAPIVersion::class)
    fun `single allOf wrapper is inlined only in 2020-12 documents and only without conflicts`(version: OpenAPIVersion) {
        val ref = "#/components/schemas/Target"
        val api = OpenAPIModel(openapi = version.value)
        api.components.schemas["Target"] = SchemaModel.SchemaModelObj<Any?>(emptyMap(), emptyList())
        api.components.schemas["Holder"] = SchemaModel.SchemaModelObj<Any?>(
            mapOf(
                "plain" to SchemaModel.AllOfSchemaModel(listOf(SchemaModel.SchemaModelRef<Any?>(ref, nullable = true)))
                    .apply { deprecated = true },
                "conflicting" to SchemaModel.AllOfSchemaModel(
                    listOf(SchemaModel.SchemaModelRef<Any?>(ref).apply { description = "Inner" })
                ).apply { description = "Outer" }
            ),
            emptyList()
        )

        val document = mapper.valueToTree<JsonNode>(api.serialize())
        assertValidDocument(document, version)
        val properties = document["components"]["schemas"]["Holder"]["properties"]
        if (version == OpenAPIVersion.V3_2) {
            assertEquals(mapper.readTree("""{"anyOf":[{"${'$'}ref":"$ref"},{"type":"null"}],"deprecated":true}"""), properties["plain"])
        } else {
            assertEquals(1, properties["plain"]["allOf"].size())
            assertEquals(true, properties["plain"]["deprecated"].asBoolean())
        }
        // Inlining would overwrite one of the descriptions, so the wrapper stays.
        assertEquals("Outer", properties["conflicting"]["description"].asText())
        assertEquals("Inner", properties["conflicting"]["allOf"][0]["description"].asText())
    }

    @Test
    fun `legacy version strings remain supported and unknown dialects fail clearly`() {
        assertEquals("3.0.0", OpenAPIModel(openapi = "3.0.0").serialize()["openapi"])
        val error = assertFailsWith<IllegalArgumentException> { OpenAPIModel(openapi = "3.1.2").serialize() }
        assertTrue(error.message!!.contains("Unsupported OpenAPI version"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["2.0", "3.1.2", "3.0.5", "3.2.2", "3.2", "3.2.1-rc1", " 3.2.1", ""])
    fun `configuration rejects unsupported versions immediately without changing the document`(version: String) {
        val api = OpenAPIModel()
        val config = OpenAPIGen.Configuration(api)
        config.openApiVersion = "3.0.1"
        assertEquals("3.0.1", config.openApiVersion)
        val error = assertFailsWith<IllegalArgumentException> { config.openApiVersion = version }
        assertTrue(error.message!!.contains("Unsupported OpenAPI version '$version'"))
        assertTrue(error.message!!.contains("Supported versions:"))
        assertEquals("3.0.1", config.openApiVersion)
        assertEquals("3.0.1", api.serialize()["openapi"])
    }

    private fun assertValidDocument(document: JsonNode, version: OpenAPIVersion) {
        val dialect = if (version == OpenAPIVersion.V3_0) SpecVersion.VersionFlag.V4 else SpecVersion.VersionFlag.V202012
        val resource = if (version == OpenAPIVersion.V3_0) "/openapi/3.0.schema.json" else "/openapi/3.2.schema.json"
        val schema = javaClass.getResourceAsStream(resource)!!.use { JsonSchemaFactory.getInstance(dialect).getSchema(it) }
        val errors = schema.validate(document)
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    private fun assertPayloadSemantics(document: JsonNode) {
        val schemaNode = document["components"]["schemas"]["Payload"].deepCopy<ObjectNode>()
        schemaNode.set<JsonNode>("components", document["components"])
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode)
        val sample = mapper.readTree(
            """
            {
              "requiredChild": {"id": "1", "parent": null},
              "child": null, "legacyChild": null, "choice": null, "text": null,
              "children": [null, {"id": "2", "parent": null}],
              "scores": {"first": null}, "nothing": null
            }
            """.trimIndent()
        )
        assertTrue(schema.validate(sample).isEmpty(), "Nullable DTOs, enums and collection elements must accept null")
        val invalidEnum = sample.deepCopy<ObjectNode>().put("choice", "UNKNOWN")
        assertTrue(schema.validate(invalidEnum).isNotEmpty(), "Enum constraints must remain effective")
        val invalidRequiredChild = sample.deepCopy<ObjectNode>().putNull("requiredChild")
        assertTrue(schema.validate(invalidRequiredChild).isNotEmpty(), "A nullable reference must not make the shared DTO nullable")
        val invalidChild = sample.deepCopy<ObjectNode>().set<JsonNode>("child", mapper.readTree("{}"))
        assertTrue(schema.validate(invalidChild).isNotEmpty(), "Referenced DTO constraints must remain effective")
    }
}
