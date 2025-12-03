package com.papsign.ktor.openapigen.parameters.parsers.converters.`object`

import com.papsign.ktor.openapigen.parameters.parsers.converters.Converter

interface MappedConverter : Converter {
    fun convert(map: Map<String, String>): Any?
}
