package com.papsign.ktor.openapigen.route

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.model.operation.OperationModel
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.modules.openapi.OperationModule

/** Sets an explicit operation ID instead of generating one from the HTTP method and path. */
fun operationId(
    operationId: String,
) = OperationId(operationId)

/** An explicit operation ID, which must be non-blank and unique within the OpenAPI document. */
data class OperationId(
    val operationId: String,
) : OperationModule,
    RouteOpenAPIModule {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
    }

    override fun configure(
        apiGen: OpenAPIGen,
        provider: ModuleProvider<*>,
        operation: OperationModel,
    ) {
        operation.operationId = operationId
    }
}
