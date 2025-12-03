package com.papsign.ktor.openapigen.model.info

import com.papsign.ktor.openapigen.model.DataModel

data class TagModel(
    val name: String,
    val description: String? = null,
    val externalDocs: ExternalDocumentationModel? = null
) : DataModel
