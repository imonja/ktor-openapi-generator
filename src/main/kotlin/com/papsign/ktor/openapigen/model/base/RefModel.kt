package com.papsign.ktor.openapigen.model.base

import com.papsign.ktor.openapigen.model.DataModel

interface RefModel<T> : DataModel {
    // Named after the "$ref" key of the OpenAPI document
    @Suppress("ktlint:standard:property-naming")
    val `$ref`: String
}
