package com.papsign.ktor.openapigen.annotations.type.file

import com.papsign.ktor.openapigen.schema.processor.SchemaProcessorAnnotation

/**
 * Restricts the declared `Content-Type` of a multipart file part.
 *
 * Values are matched ignoring parameters and case, wildcard subtypes (`*`) are supported.
 * A part without a `Content-Type` header is rejected.
 * Applicable to [com.papsign.ktor.openapigen.content.type.multipart.ContentInputStream] and
 * [com.papsign.ktor.openapigen.content.type.multipart.NamedFileInputStream] properties of a
 * [com.papsign.ktor.openapigen.content.type.multipart.FormDataRequest], cannot be combined with
 * [com.papsign.ktor.openapigen.content.type.multipart.PartEncoding].
 */
@Target(AnnotationTarget.PROPERTY)
@SchemaProcessorAnnotation(AllowedContentTypesProcessor::class)
annotation class AllowedContentTypes(
    vararg val value: String,
    val errorMessage: String = "",
)
