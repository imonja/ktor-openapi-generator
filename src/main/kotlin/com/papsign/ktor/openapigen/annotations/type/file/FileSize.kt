package com.papsign.ktor.openapigen.annotations.type.file

import com.papsign.ktor.openapigen.schema.processor.SchemaProcessorAnnotation

/**
 * Limits the size in bytes of a multipart file part.
 *
 * Applicable to [java.io.InputStream], [com.papsign.ktor.openapigen.content.type.multipart.ContentInputStream]
 * and [com.papsign.ktor.openapigen.content.type.multipart.NamedFileInputStream] properties of a
 * [com.papsign.ktor.openapigen.content.type.multipart.FormDataRequest].
 * The part is read into memory (at most [max] bytes) before the handler is called,
 * so the handler receives a fully validated stream. Empty files are rejected unless [min] is 0.
 */
@Target(AnnotationTarget.PROPERTY)
@SchemaProcessorAnnotation(FileSizeProcessor::class)
annotation class FileSize(
    val min: Long = 1,
    val max: Long = Long.MAX_VALUE,
    val errorMessage: String = ""
)
