package com.papsign.ktor.openapigen.annotations.type.number.integer

import com.papsign.ktor.openapigen.annotations.type.number.NumberConstraintProcessor
import com.papsign.ktor.openapigen.getKType

abstract class IntegerNumberConstraintProcessor<A : Annotation> : NumberConstraintProcessor<A>(
    listOf(
        getKType<Int>(),
        getKType<Long>(),
        getKType<Float>(),
        getKType<Double>()
    )
)
