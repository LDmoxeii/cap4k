package com.only4.cap4k.ddd.core.application.context

enum class ExecutionContextBoundary {
    RELIABLE_COMMAND,
    RELIABLE_DOMAIN_EVENT,
    INTEGRATION_EVENT,
    RPC,
}

enum class UnknownExecutionContextElementPolicy {
    REJECT,
    IGNORE,
}

data class EncodedExecutionContextElement(
    val name: String,
    val version: Int,
    val value: String,
) {
    init {
        require(name.isNotBlank()) { "Encoded ExecutionContext element name must not be blank" }
        require(version > 0) { "Encoded ExecutionContext element version must be positive" }
    }
}

interface ExecutionContextElementCodec<T : ExecutionContextElement> {
    val key: ExecutionContextKey<T>
    val version: Int
    val boundaries: Set<ExecutionContextBoundary>

    fun encode(element: T): String

    fun decode(value: String): T
}

class ExecutionContextDecodingException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Registry for versioned transport codecs. The highest registered version is used for encoding.
 */
class ExecutionContextCodecRegistry(
    codecs: List<ExecutionContextElementCodec<*>>,
) {
    private val codecsByName: Map<String, Map<Int, ExecutionContextElementCodec<*>>>
    private val currentCodecByKey: Map<ExecutionContextKey<*>, ExecutionContextElementCodec<*>>

    init {
        val grouped = codecs.groupBy { it.key.name }
        grouped.forEach { (name, namedCodecs) ->
            require(namedCodecs.all { it.version > 0 }) {
                "ExecutionContext codec versions for '$name' must be positive"
            }
            val expectedKey = namedCodecs.first().key
            require(namedCodecs.all { it.key == expectedKey }) {
                "ExecutionContext wire name '$name' is registered with incompatible key types"
            }
            val duplicateVersion = namedCodecs.groupBy { it.version }.entries.firstOrNull { it.value.size > 1 }
            require(duplicateVersion == null) {
                "ExecutionContext wire name '$name' has duplicate codec version ${duplicateVersion?.key}"
            }
        }
        codecsByName = grouped.mapValues { (_, namedCodecs) -> namedCodecs.associateBy { it.version } }
        currentCodecByKey = grouped.values.associate { namedCodecs ->
            val current = namedCodecs.maxBy { it.version }
            current.key to current
        }
    }

    fun encode(
        snapshot: ExecutionContextSnapshot,
        boundary: ExecutionContextBoundary,
    ): List<EncodedExecutionContextElement> = snapshot.entries()
        .mapNotNull { (key, value) ->
            val codec = currentCodecByKey[key] ?: return@mapNotNull null
            if (boundary !in codec.boundaries) return@mapNotNull null
            EncodedExecutionContextElement(
                name = key.name,
                version = codec.version,
                value = encode(codec, value),
            )
        }
        .sortedBy { it.name }

    fun decodeReliable(
        elements: Collection<EncodedExecutionContextElement>,
        boundary: ExecutionContextBoundary,
    ): ExecutionContextSnapshot = decode(elements, boundary, UnknownExecutionContextElementPolicy.REJECT)

    fun decodeExternal(
        elements: Collection<EncodedExecutionContextElement>,
        boundary: ExecutionContextBoundary,
    ): ExecutionContextSnapshot = decode(elements, boundary, UnknownExecutionContextElementPolicy.IGNORE)

    fun decode(
        elements: Collection<EncodedExecutionContextElement>,
        boundary: ExecutionContextBoundary,
        unknownElementPolicy: UnknownExecutionContextElementPolicy,
    ): ExecutionContextSnapshot {
        val duplicate = elements.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            throw ExecutionContextDecodingException("Duplicate ExecutionContext element '${duplicate.key}'")
        }

        val builder = ExecutionContextSnapshot.builder()
        elements.forEach { encoded ->
            val versions = codecsByName[encoded.name]
            if (versions == null) {
                if (unknownElementPolicy == UnknownExecutionContextElementPolicy.REJECT) {
                    throw ExecutionContextDecodingException("Unknown ExecutionContext element '${encoded.name}'")
                }
                return@forEach
            }
            val codec = versions[encoded.version]
                ?: throw ExecutionContextDecodingException(
                    "Unsupported ExecutionContext element '${encoded.name}' version ${encoded.version}",
                )
            if (boundary !in codec.boundaries) {
                throw ExecutionContextDecodingException(
                    "ExecutionContext element '${encoded.name}' is not allowed on boundary $boundary",
                )
            }
            val value = try {
                codec.decode(encoded.value)
            } catch (ex: Exception) {
                throw ExecutionContextDecodingException(
                    "Malformed ExecutionContext element '${encoded.name}' version ${encoded.version}",
                    ex,
                )
            }
            put(builder, codec, value)
        }
        return builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun encode(codec: ExecutionContextElementCodec<*>, value: ExecutionContextElement): String =
        (codec as ExecutionContextElementCodec<ExecutionContextElement>).encode(value)

    @Suppress("UNCHECKED_CAST")
    private fun put(
        builder: ExecutionContextSnapshot.Builder,
        codec: ExecutionContextElementCodec<*>,
        value: ExecutionContextElement,
    ) {
        val typedCodec = codec as ExecutionContextElementCodec<ExecutionContextElement>
        check(typedCodec.key.type.isInstance(value)) {
            "ExecutionContext codec '${typedCodec.key.name}' decoded ${value.javaClass.name}, " +
                "expected ${typedCodec.key.type.name}"
        }
        builder.put(typedCodec.key, value)
    }
}
