package com.papsign.ktor.openapigen.annotations.type.file

import com.papsign.ktor.openapigen.classLogger
import com.papsign.ktor.openapigen.model.schema.DataFormat
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessor
import kotlin.reflect.KType

/** The part encoding carries the full list; a single concrete type also becomes the 3.1+ `contentMediaType`. */
object AllowedContentTypesProcessor : SchemaProcessor<AllowedContentTypes> {

    private val log = classLogger()

    override fun process(model: SchemaModel<*>, type: KType, annotation: AllowedContentTypes): SchemaModel<*> {
        if (model !is SchemaModel.SchemaModelLitteral<*> || model.format != DataFormat.binary) {
            log.warn("${AllowedContentTypes::class} can only be used on binary stream types, ignoring it on $type")
            return model
        }
        annotation.value.singleOrNull()?.takeUnless { it.contains('*') }?.let { model.contentMediaType = it }
        return model
    }
}
