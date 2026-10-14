package com.papsign.ktor.openapigen.annotations.type.string.example

import com.papsign.ktor.openapigen.model.schema.DataType
import com.papsign.ktor.openapigen.model.schema.Discriminator
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessor
import com.papsign.ktor.openapigen.schema.processor.SchemaProcessorAnnotation
import kotlin.reflect.KType

@Target(AnnotationTarget.CLASS)
@SchemaProcessorAnnotation(LegacyDiscriminatorProcessor::class)
annotation class DiscriminatorAnnotation(val fieldName: String = "type")

// Difference between legacy mode and current
// https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/typescript.md
// For non-legacy mapping is from sub-types to base-type by allOf
// This implementation follow previous implementation, so we need to have
// - discriminatorName in each type Parameters array, required in subtypes
// - { discriminator: { propertyName: discriminatorName } } in the sealed base type
object LegacyDiscriminatorProcessor : SchemaProcessor<DiscriminatorAnnotation> {
    override fun process(model: SchemaModel<*>, type: KType, annotation: DiscriminatorAnnotation): SchemaModel<*> {
        val mapElement = (
            annotation.fieldName to SchemaModel.SchemaModelLitteral<String>(DataType.string)
            )

        if (model is SchemaModel.OneSchemaModelOf<*>) {
            return SchemaModel.OneSchemaModelOf(
                model.oneOf,
                mapOf(mapElement),
                Discriminator(annotation.fieldName)
            )
        }

        if (model is SchemaModel.SchemaModelObj<*>) {
            return model.copy(
                properties = model.properties + mapElement,
                required = if (annotation.fieldName in model.required) model.required else model.required + annotation.fieldName
            )
        }

        return model
    }
}
