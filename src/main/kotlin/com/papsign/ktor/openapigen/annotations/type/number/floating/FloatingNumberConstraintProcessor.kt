package com.papsign.ktor.openapigen.annotations.type.number.floating

import com.papsign.ktor.openapigen.annotations.type.number.NumberConstraintProcessor
import com.papsign.ktor.openapigen.getKType

abstract class FloatingNumberConstraintProcessor<A : Annotation> : NumberConstraintProcessor<A>(
    listOf(
        getKType<Float>(),
        getKType<Double>()
    )
)
