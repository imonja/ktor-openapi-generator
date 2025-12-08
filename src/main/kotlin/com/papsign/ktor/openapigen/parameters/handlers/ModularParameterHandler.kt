package com.papsign.ktor.openapigen.parameters.handlers

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.mapping.openAPIName
import com.papsign.ktor.openapigen.annotations.mapping.remapOpenAPINames
import com.papsign.ktor.openapigen.annotations.parameters.HeaderParam
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.annotations.parameters.apiParam
import com.papsign.ktor.openapigen.exceptions.OpenAPIRequiredFieldException
import com.papsign.ktor.openapigen.memberProperties
import com.papsign.ktor.openapigen.model.operation.ParameterLocation
import com.papsign.ktor.openapigen.model.operation.ParameterModel
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.ofType
import com.papsign.ktor.openapigen.parameters.parsers.builders.Builder
import com.papsign.ktor.openapigen.schema.builder.provider.FinalSchemaBuilderProviderModule
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.util.toMap
import java.util.Locale
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.withNullability

class ModularParameterHandler<T>(val parsers: Map<KParameter, Builder<*>>, val constructor: KFunction<T>) :
    ParameterHandler<T> {

    override fun parse(parameters: Parameters, headers: Headers): T {
        val allParams = parameters.toMap() + headers.toMap().entries.groupBy {
            it.key.lowercase(Locale.getDefault())
        }.mapValues { it.value.flatMap { it.value } }

        val parsedValues = mutableMapOf<KParameter, Any?>()

        for ((param, parser) in parsers) {
            val value = parser.build(
                param.name.toString(),
                param.remapOpenAPINames(allParams)
            )

            if (value != null) {
                // Parameter has a value from the request
                parsedValues[param] = value
            } else if (param.type.isMarkedNullable) {
                // Parameter is nullable but no value provided
                if (param.isOptional) {
                    // Parameter has a default value, skip it so callBy uses the default
                    // Don't add to parsedValues map
                } else {
                    // Parameter is nullable but has no default, use null
                    parsedValues[param] = null
                }
            } else {
                // Required non-nullable parameter with no value
                throw OpenAPIRequiredFieldException("""The field ${param.openAPIName ?: "unknown field"} is required""")
            }
        }

        return constructor.callBy(parsedValues)
    }

    private fun extractDefaultValue(param: KParameter): Any? {
        return try {
            // Check if parameter has a default value (both optional nullable and non-nullable with defaults)
            if (!param.isOptional && !param.type.isMarkedNullable) return null

            // Create a minimal parameter map with all required parameters (except the one we're extracting)
            val minimalParams = constructor.parameters.mapNotNull { p ->
                if (!p.isOptional && p != param) {
                    // For required parameters, we need to provide dummy values
                    val dummyValue = if (p.type.isMarkedNullable) {
                        // For nullable required parameters, use null
                        null
                    } else {
                        // For non-nullable required parameters, create dummy values
                        when (p.type.classifier) {
                            String::class -> ""
                            Int::class -> 0
                            Long::class -> 0L
                            Boolean::class -> false
                            Double::class -> 0.0
                            Float::class -> 0.0f
                            java.util.UUID::class -> java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
                            else -> {
                                // Try to create a default instance for other types
                                try {
                                    val kclass = p.type.classifier as? kotlin.reflect.KClass<*>
                                    kclass?.objectInstance ?: kclass?.constructors?.firstOrNull { it.parameters.isEmpty() }?.call()
                                } catch (e: Exception) {
                                    // If we can't create a dummy value, skip this parameter approach
                                    return@extractDefaultValue null
                                }
                            }
                        }
                    }
                    p to dummyValue
                } else {
                    null
                }
            }.toMap()

            // Try to create an instance with minimal parameters to get the default value
            val instance = constructor.callBy(minimalParams)

            // Use reflection to get the property value
            val property = constructor.returnType.classifier?.let { clazz ->
                (clazz as? kotlin.reflect.KClass<*>)?.memberProperties?.find { it.name == param.name }
            }
            @Suppress("UNCHECKED_CAST")
            (property as? kotlin.reflect.KProperty1<Any, *>)?.get(instance as Any)
        } catch (e: Exception) {
            // If we can't extract the default value, return null
            null
        }
    }

    override fun getParameters(apiGen: OpenAPIGen, provider: ModuleProvider<*>): List<ParameterModel<*>> {
        val schemaBuilder = provider.ofType<FinalSchemaBuilderProviderModule>().last().provide(apiGen, provider)
        val ktype = constructor.returnType

        fun createParam(param: KParameter, `in`: ParameterLocation, config: (ParameterModel<*>) -> Unit): ParameterModel<*> {
            return ParameterModel<Any>(
                param.openAPIName.toString(),
                `in`,
                !param.type.isMarkedNullable && !param.isOptional
            ).also {
                @Suppress("UNCHECKED_CAST")
                it.schema = schemaBuilder.build(
                    param.type.withNullability(false),
                    ktype.memberProperties.find { it.name == param.name }?.source?.annotations ?: listOf()
                ) as SchemaModel<Any>
                config(it)
            }
        }

        fun HeaderParam.createParam(param: KParameter): ParameterModel<*> {
            val parser = parsers[param]!!
            return createParam(param, apiParam.`in`) {
                it.description = description
                it.allowEmptyValue = allowEmptyValues
                it.deprecated = deprecated
                it.style = parser.style
                it.explode = parser.explode

                // Set default value if parameter has one
                if (param.isOptional) {
                    val defaultValue = extractDefaultValue(param)
                    @Suppress("UNCHECKED_CAST")
                    (it as ParameterModel<Any?>).default = defaultValue
                }
            }
        }

        fun QueryParam.createParam(param: KParameter): ParameterModel<*> {
            val parser = parsers[param]!!
            return createParam(param, apiParam.`in`) {
                it.description = description
                it.allowEmptyValue = allowEmptyValues
                it.deprecated = deprecated
                it.style = parser.style
                it.explode = parser.explode

                // Set default value if parameter has one
                if (param.isOptional) {
                    val defaultValue = extractDefaultValue(param)
                    @Suppress("UNCHECKED_CAST")
                    (it as ParameterModel<Any?>).default = defaultValue
                }
            }
        }

        fun PathParam.createParam(param: KParameter): ParameterModel<*> {
            val parser = parsers[param]!!
            return createParam(param, apiParam.`in`) {
                it.description = description
                it.deprecated = deprecated
                it.style = parser.style
                it.explode = parser.explode
            }
        }

        return constructor.parameters.map {
            it.findAnnotation<HeaderParam>()?.createParam(it)
                ?: it.findAnnotation<PathParam>()?.createParam(it)
                ?: it.findAnnotation<QueryParam>()?.createParam(it)
                ?: error(
                    "API routes with ${constructor.returnType} must have parameters annotated with one of ${paramAnnotationClasses.map { it.simpleName }}"
                )
        }
    }

    companion object {
        private val paramAnnotationClasses = hashSetOf(HeaderParam::class, PathParam::class, QueryParam::class)
    }
}
