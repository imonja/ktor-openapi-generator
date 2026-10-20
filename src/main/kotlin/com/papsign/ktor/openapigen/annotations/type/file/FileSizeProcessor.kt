package com.papsign.ktor.openapigen.annotations.type.file

import com.papsign.ktor.openapigen.classLogger
import com.papsign.ktor.openapigen.model.schema.DataFormat
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessor
import kotlin.reflect.KType

object FileSizeProcessor : SchemaProcessor<FileSize> {

    private val log = classLogger()

    override fun process(model: SchemaModel<*>, type: KType, annotation: FileSize): SchemaModel<*> {
        if (model !is SchemaModel.SchemaModelLitteral<*> || model.format != DataFormat.binary) {
            log.warn("${FileSize::class} can only be used on binary stream types, ignoring it on $type")
            return model
        }
        // JSON Schema has no size keyword for binary content: maxLength would count characters.
        val extensions = LinkedHashMap(model.extensions.orEmpty())
        if (annotation.min > 1) extensions["x-min-file-size"] = annotation.min
        if (annotation.max != Long.MAX_VALUE) extensions["x-max-file-size"] = annotation.max
        model.extensions = extensions
        model.description = listOfNotNull(model.description, annotation.describe()).joinToString("\n\n")
        return model
    }

    private fun FileSize.describe(): String? {
        val hasMin = min > 1
        val hasMax = max != Long.MAX_VALUE
        return when {
            hasMin && hasMax -> "File size: ${formatBytes(min)} to ${formatBytes(max)}"
            hasMax -> "Max file size: ${formatBytes(max)}"
            hasMin -> "Min file size: ${formatBytes(min)}"
            else -> null
        }
    }

    internal fun formatBytes(bytes: Long): String {
        val kb = 1024L
        val mb = kb * kb
        return when {
            bytes >= mb && bytes % mb == 0L -> "${bytes / mb} MB"
            bytes >= kb && bytes % kb == 0L -> "${bytes / kb} KB"
            bytes == 1L -> "1 byte"
            else -> "$bytes bytes"
        }
    }
}
