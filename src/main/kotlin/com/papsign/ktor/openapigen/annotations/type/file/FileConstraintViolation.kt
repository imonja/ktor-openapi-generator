package com.papsign.ktor.openapigen.annotations.type.file

import com.papsign.ktor.openapigen.annotations.type.common.ConstraintViolation
import io.ktor.http.ContentType

/** [actual] is null when reading stopped after exceeding [FileSize.max]. */
class FileSizeConstraintViolation(val actual: Long?, val constraint: FileSize) : ConstraintViolation(
    if (actual == null) {
        "Constraint violation: the file size should be at most ${constraint.max} bytes, but it is larger"
    } else {
        "Constraint violation: the file size should be at least ${constraint.min} bytes, but it is $actual"
    },
    constraint.errorMessage
)

class ContentTypeConstraintViolation(val actual: ContentType?, val constraint: AllowedContentTypes) : ConstraintViolation(
    "Constraint violation: the content type should be one of ${constraint.value.joinToString()}, but it is ${
    actual?.toString() ?: "missing"
    }",
    constraint.errorMessage
)
