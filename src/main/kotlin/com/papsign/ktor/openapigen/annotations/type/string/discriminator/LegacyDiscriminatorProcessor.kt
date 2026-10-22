package com.papsign.ktor.openapigen.annotations.type.string.example

import com.papsign.ktor.openapigen.model.schema.DataType
import com.papsign.ktor.openapigen.model.schema.Discriminator
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessor
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessorAnnotation
import kotlin.reflect.KType

@Target(AnnotationTarget.CLASS)
@SchemaProcessorAnnotation(LegacyDiscriminatorProcessor::class)
annotation class DiscriminatorAnnotation(
    val fieldName: String = "type",
)

// Difference between legacy mode and current
// https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/typescript.md
// For non-legacy mapping is from sub-types to base-type by allOf
// This implementation follow previous implementation, so we need to have
// - discriminatorName in each type Parameters array
// - { discriminator: { propertyName: discriminatorName } } in the sealed base type
object LegacyDiscriminatorProcessor : SchemaProcessor<DiscriminatorAnnotation> {
    override fun process(
        model: SchemaModel<*>,
        type: KType,
        annotation: DiscriminatorAnnotation,
    ): SchemaModel<*> {
        val mapElement = (
            annotation.fieldName to SchemaModel.SchemaModelLitteral<String>(DataType.string)
        )

        if (model is SchemaModel.OneSchemaModelOf<*>) {
            return SchemaModel.OneSchemaModelOf(
                model.oneOf,
                mapOf(mapElement),
                Discriminator(annotation.fieldName),
            )
        }

        if (model is SchemaModel.SchemaModelObj<*>) {
            // Not required: Jackson may write the type id under another name (e.g. "@type"), and clients
            // generated from a required property would then fail to decode the response.
            return model.copy(properties = model.properties + mapElement)
        }

        return model
    }
}
