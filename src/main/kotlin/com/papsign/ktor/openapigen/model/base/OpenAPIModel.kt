package com.papsign.ktor.openapigen.model.base

import com.papsign.ktor.openapigen.OpenAPIVersion
import com.papsign.ktor.openapigen.model.DataModel
import com.papsign.ktor.openapigen.model.info.ExternalDocumentationModel
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.model.info.TagModel
import com.papsign.ktor.openapigen.model.operation.OperationModel
import com.papsign.ktor.openapigen.model.security.SecurityModel
import com.papsign.ktor.openapigen.model.server.ServerModel
import java.util.Locale

data class OpenAPIModel(
    var info: InfoModel = InfoModel(),
    var openapi: String = OpenAPIVersion.V3_2.value,
    var servers: MutableList<ServerModel> = mutableListOf(),
    var paths: MutableMap<String, PathItemModel> = mutableMapOf(),
    var components: ComponentsModel = ComponentsModel(),
    var security: LinkedHashSet<SecurityModel> = LinkedHashSet(),
    var tags: LinkedHashSet<TagModel> = LinkedHashSet(),
    var externalDocs: ExternalDocumentationModel? = null,
) : DataModel {
    override fun serialize(): Map<String, Any?> {
        // A `major.minor` value such as "3.1" is written as the latest version of its family.
        val resolved = OpenAPIVersion.resolve(openapi)
        val operationsById = mutableMapOf<String, String>()
        paths.forEach { (path, item) ->
            item.forEach { (method, value) ->
                val id = (value as? OperationModel)?.operationId
                if (id != null) {
                    val operation = "${method.uppercase(Locale.ROOT)} $path"
                    val previous = operationsById.put(id, operation)
                    check(previous == null) { "Duplicate operationId '$id' for $previous and $operation; set a unique operationId explicitly" }
                }
            }
        }
        val document = super.serialize().toMutableMap().apply { this["openapi"] = resolved }
        return OpenAPIDocumentSerializer(OpenAPIVersion.fromValue(resolved)).serialize(document)
    }
}
