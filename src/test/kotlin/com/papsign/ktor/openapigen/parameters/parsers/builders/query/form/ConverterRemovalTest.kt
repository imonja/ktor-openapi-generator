package com.papsign.ktor.openapigen.parameters.parsers.builders.query.form

import com.papsign.ktor.openapigen.parameters.parsers.converters.Converter
import com.papsign.ktor.openapigen.parameters.parsers.converters.ConverterSelector
import com.papsign.ktor.openapigen.parameters.parsers.converters.primitive.PrimitiveConverter
import com.papsign.ktor.openapigen.parameters.parsers.converters.primitive.PrimitiveConverterFactory
import com.papsign.ktor.openapigen.parameters.parsers.testSelector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.reflect.KType

class ConverterRemovalTest {
    @BeforeEach
    fun before() {
        PrimitiveConverterFactory.injectConverterAfter(PrimitiveConverter::class, AnyToBooleanConverter)
        PrimitiveConverterFactory.removeConverter(PrimitiveConverter::class)
    }

    @AfterEach
    fun after() {
        PrimitiveConverterFactory.injectConverterBefore(AnyToBooleanConverter::class, PrimitiveConverter)
        PrimitiveConverterFactory.removeConverter(AnyToBooleanConverter::class)
    }

    @Test
    fun testConverterRemoval() {
        val values = listOf("random", 1, UUID.randomUUID(), OffsetDateTime.now())

        values.forEach {
            val key = "key"
            val expected = true
            val parse = mapOf(
                key to listOf(it.toString())
            )

            FormBuilderFactory.testSelector(expected, key, parse, true)
        }
    }
}

private object AnyToBooleanConverter : ConverterSelector, Converter {
    override fun convert(value: String): Any = true

    override fun canHandle(type: KType): Boolean = true

    override fun create(type: KType): Converter = this
}

