package com.papsign.ktor.openapigen.content.type.multipart

import com.papsign.ktor.openapigen.*
import com.papsign.ktor.openapigen.annotations.mapping.openAPIName
import com.papsign.ktor.openapigen.annotations.type.file.AllowedContentTypes
import com.papsign.ktor.openapigen.annotations.type.file.ContentTypeConstraintViolation
import com.papsign.ktor.openapigen.annotations.type.file.FileSize
import com.papsign.ktor.openapigen.annotations.type.file.FileSizeConstraintViolation
import com.papsign.ktor.openapigen.content.type.BodyParser
import com.papsign.ktor.openapigen.content.type.ContentTypeProvider
import com.papsign.ktor.openapigen.exceptions.OpenAPIRequiredFieldException
import com.papsign.ktor.openapigen.exceptions.assertContent
import com.papsign.ktor.openapigen.model.operation.MediaTypeEncodingModel
import com.papsign.ktor.openapigen.model.operation.MediaTypeModel
import com.papsign.ktor.openapigen.model.schema.SchemaModel
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.ofType
import com.papsign.ktor.openapigen.route.CallResources
import com.papsign.ktor.openapigen.schema.builder.provider.FinalSchemaBuilderProviderModule
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.RoutingContext
import io.ktor.util.asStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

object MultipartFormDataContentProvider : BodyParser, OpenAPIGenModuleExtension {

    override fun <T : Any> getParseableContentTypes(type: KType): List<ContentType> {
        return listOf(ContentType.MultiPart.FormData)
    }

    data class MultipartCVT<T>(val default: T?, val type: KType, val clazz: KClass<*>, val serializer: (T) -> String, val parser: (String) -> T)

    inline fun <reified T> cvt(noinline serializer: (T) -> String, noinline parser: (String) -> T, default: T? = null) = MultipartCVT(
        default,
        getKType<T>(),
        T::class,
        serializer,
        parser
    )

    private val streamTypes = setOf(
        getKType<InputStream>(),
        getKType<ContentInputStream>(),
        getKType<NamedFileInputStream>(),
        getKType<InputStream?>(),
        getKType<ContentInputStream?>(),
        getKType<NamedFileInputStream?>()
    )

    private val conversions = setOf(
        cvt({ it }, { it }, ""),
        cvt(Int::toString, String::toInt, 0),
        cvt(Long::toString, String::toLong, 0),
        cvt(Float::toString, String::toFloat, .0f),
        cvt(Double::toString, String::toDouble, .0),
        cvt(Instant::toString, Instant::parse),
        cvt(Boolean::toString, String::toBoolean, false)
    )

    private val conversionsByType = conversions.associateBy { it.type.withNullability(false) } + conversions.associateBy {
        it.type.withNullability(
            true
        )
    }

    private val nonNullTypes = streamTypes + conversionsByType.keys

    private val allowedTypes = nonNullTypes

    private val typeContentTypes = HashMap<KType, Map<String, MediaTypeEncodingModel>>()

    private class FileConstraints(val size: FileSize?, val contentTypes: AllowedContentTypes?)

    private val fileConstraintsByClass = ConcurrentHashMap<KClass<*>, Map<String, FileConstraints>>()

    /** Keyed by part name; only parts with file annotations are present. */
    private fun fileConstraints(clazz: KClass<*>): Map<String, FileConstraints> = fileConstraintsByClass.getOrPut(clazz) {
        val properties = clazz.memberProperties.associateBy { it.name }
        clazz.primaryConstructor?.parameters.orEmpty().mapNotNull { param ->
            val property = properties[param.name] ?: return@mapNotNull null
            val constraints = FileConstraints(property.findAnnotation(), property.findAnnotation())
            if (constraints.size == null && constraints.contentTypes == null) {
                null
            } else {
                (param.openAPIName ?: return@mapNotNull null) to constraints
            }
        }.toMap()
    }

    override suspend fun <T : Any> parseBody(clazz: KType, request: RoutingContext): T {
        @Suppress("UNCHECKED_CAST")
        val ctor = (clazz.classifier as KClass<T>).primaryConstructor!!
        val names = ctor.parameters.mapNotNullTo(HashSet()) { it.openAPIName }
        val constraints = fileConstraints(clazz.jvmErasure)
        val objectMap = HashMap<String, Any>()
        try {
            // Ktor parses in a child coroutine: without a scope its failure would cancel the call instead of reaching this catch.
            coroutineScope {
                request.call.receiveMultipart().forEachPart {
                    val name = it.name
                    if (name == null || name !in names) {
                        it.dispose()
                    } else {
                        when (it) {
                            is PartData.FormItem -> {
                                objectMap[name] = it.value
                            }
                            is PartData.FileItem -> {
                                val channel = it.provider()
                                objectMap[name] = receiveStream(request, it, constraints[name]) { buffer -> channel.readAvailable(buffer) }
                            }
                            is PartData.BinaryItem -> {
                                val stream = it.provider().asStream()
                                objectMap[name] = receiveStream(request, it, constraints[name]) { buffer -> stream.read(buffer) }
                            }
                            else -> {}
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw BadRequestException("Malformed multipart request: ${e.message}", e)
        }
        // Same rules as parameters: a Kotlin default, then null, otherwise the part is required.
        val args = HashMap<KParameter, Any?>()
        ctor.parameters.forEach { param ->
            val name = param.openAPIName ?: param.name.orEmpty()
            val value = objectMap[name]?.let { convert(param, name, it) }
            when {
                value != null -> args[param] = value
                param.isOptional -> {}
                param.type.isMarkedNullable -> args[param] = null
                else -> throw OpenAPIRequiredFieldException("The field $name is required")
            }
        }
        return ctor.callBy(args)
    }

    private fun convert(param: KParameter, name: String, raw: Any): Any? {
        if (param.type in streamTypes) {
            return when {
                raw is InputStream -> raw
                // Forms send an empty text part for an optional file that was not chosen.
                raw == "" && param.type.isMarkedNullable -> null
                else -> throw BadRequestException("The field $name must be a file part")
            }
        }
        if (raw !is String) throw BadRequestException("The field $name must be a text part")
        val cvt = conversionsByType[param.type] ?: error("Unhandled Type ${param.type}")
        return try {
            cvt.parser(raw)
        } catch (e: RuntimeException) {
            throw BadRequestException("The field $name has an invalid value", e)
        }
    }

    /** Larger stream parts are spooled to a temporary file, like servlet multipart handling does. */
    private const val MEMORY_THRESHOLD_BYTES = 256 * 1024

    /**
     * Reads a stream part completely while it is still readable: Ktor disposes a part once the next one is requested,
     * and the parser cannot move past an unread part. [FileSize] stops reading one byte past its maximum.
     */
    private suspend fun receiveStream(request: RoutingContext, part: PartData, constraints: FileConstraints?, read: suspend (buffer: ByteArray) -> Int): ContentInputStream {
        try {
            constraints?.contentTypes?.let { allowed ->
                val actual = part.contentType
                if (actual == null || allowed.value.none { actual.withoutParameters().match(it) }) {
                    throw ContentTypeConstraintViolation(actual, allowed)
                }
            }
            val size = constraints?.size
            val limit = if (size == null || size.max == Long.MAX_VALUE) Long.MAX_VALUE else size.max + 1
            val (length, stream) = spool(CallResources.of(request.call), limit, read)
            if (size != null) {
                if (length > size.max) throw FileSizeConstraintViolation(null, size)
                if (length < size.min) throw FileSizeConstraintViolation(length, size)
            }
            return when (part) {
                is PartData.FileItem -> NamedFileInputStream(part.originalFileName, part.contentType, stream)
                else -> ContentInputStream(part.contentType, stream)
            }
        } finally {
            part.dispose()
        }
    }

    private suspend fun spool(resources: CallResources, limit: Long, read: suspend (buffer: ByteArray) -> Int): Pair<Long, InputStream> {
        val memory = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var file: Path? = null
        var output: OutputStream? = null
        var length = 0L
        try {
            while (length < limit) {
                val count = read(buffer)
                if (count < 0) break
                length += count
                if (output == null && memory.size() + count > MEMORY_THRESHOLD_BYTES) {
                    val path = withContext(Dispatchers.IO) { Files.createTempFile("openapigen-multipart-", ".tmp") }
                    resources.register { Files.deleteIfExists(path) }
                    file = path
                    output = withContext(Dispatchers.IO) { Files.newOutputStream(path).also { memory.writeTo(it) } }
                }
                if (output != null) {
                    withContext(Dispatchers.IO) { output.write(buffer, 0, count) }
                } else {
                    memory.write(buffer, 0, count)
                }
            }
        } finally {
            output?.let { withContext(Dispatchers.IO) { it.close() } }
        }
        val stream = if (file == null) {
            ByteArrayInputStream(memory.toByteArray())
        } else {
            withContext(Dispatchers.IO) { Files.newInputStream(file, StandardOpenOption.DELETE_ON_CLOSE) }
                .also { resources.register(it) }
        }
        return length to stream
    }

    private val contentTypeStreamTypes = setOf(getKType<ContentInputStream>(), getKType<NamedFileInputStream>())

    /** A misplaced file annotation would silently skip validation, so it fails route registration instead. */
    private fun checkFileAnnotations(clazz: KClass<*>) {
        clazz.memberProperties.forEach { property ->
            val name = "${clazz.simpleName}.${property.name}"
            val type = property.returnType.withNullability(false)
            property.findAnnotation<FileSize>()?.let { size ->
                assertContent(type in streamTypes) {
                    "$name: ${FileSize::class.simpleName} can only be used on types: ${streamTypes.filterNot { it.isMarkedNullable }}"
                }
                assertContent(size.min >= 0 && size.min <= size.max) {
                    "$name: ${FileSize::class.simpleName} requires 0 <= min <= max, got min=${size.min}, max=${size.max}"
                }
            }
            property.findAnnotation<AllowedContentTypes>()?.let { allowed ->
                assertContent(type in contentTypeStreamTypes) {
                    "$name: ${AllowedContentTypes::class.simpleName} can only be used on types: $contentTypeStreamTypes"
                }
                assertContent(allowed.value.isNotEmpty()) {
                    "$name: ${AllowedContentTypes::class.simpleName} requires at least one content type"
                }
                allowed.value.forEach {
                    assertContent(runCatching { ContentType.parse(it) }.isSuccess) {
                        "$name: ${AllowedContentTypes::class.simpleName} has an invalid content type: $it"
                    }
                }
            }
        }
    }

    override fun <T> getMediaType(
        type: KType,
        apiGen: OpenAPIGen,
        provider: ModuleProvider<*>,
        example: T?,
        usage: ContentTypeProvider.Usage
    ): Map<ContentType, MediaTypeModel<T>>? {
        if (type == unitKType) return null
        val formContentType = type.jvmErasure.findAnnotation<FormDataRequest>()?.type?.contentType ?: return null
        val ctor = type.jvmErasure.primaryConstructor
        when (usage) {
            ContentTypeProvider.Usage.PARSE -> {
                assertContent(ctor != null) {
                    "${this::class.simpleName} requires a primary constructor"
                }
                assertContent(allowedTypes.containsAll(ctor!!.parameters.map { it.type.withNullability(false) })) {
                    "${this::class.simpleName} all constructor parameters must be of types: $allowedTypes"
                }
                checkFileAnnotations(type.jvmErasure)
            }
            ContentTypeProvider.Usage.SERIALIZE -> {
                assertContent(allowedTypes.containsAll(ctor!!.parameters.map { it.type.withNullability(false) })) {
                    "${this::class.simpleName} only supports DTOs containing following types: ${allowedTypes.joinToString()}"
                }
            }
        }

        val contentTypes = synchronized(typeContentTypes) {
            typeContentTypes.getOrPut(type) {
                type.jvmErasure.memberProperties
                    .associateBy { it.name }
                    .mapValues { (name, property) ->
                        val encoding = property.findAnnotation<PartEncoding>()
                        val allowed = property.findAnnotation<AllowedContentTypes>()
                        assertContent(encoding == null || allowed == null) {
                            "${type.jvmErasure.simpleName}.$name: ${PartEncoding::class.simpleName} and ${AllowedContentTypes::class.simpleName} cannot be combined"
                        }
                        encoding?.contentType ?: allowed?.value?.joinToString(", ")
                    }
                    .filterValues { it != null }
                    .mapValues { MediaTypeEncodingModel(it.value!!) }
            }.toMap()
        }
        val schemaBuilder = provider.ofType<FinalSchemaBuilderProviderModule>().last().provide(apiGen, provider)
        @Suppress("UNCHECKED_CAST")
        return mapOf(formContentType to MediaTypeModel(schemaBuilder.build(type) as SchemaModel<T>, example, null, contentTypes))
    }
}
