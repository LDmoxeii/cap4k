package com.only4.cap4k.ddd.core.application.context

/**
 * Marker for immutable attribution values carried by [ExecutionContextSnapshot].
 */
interface ExecutionContextElement

data class ExecutionContextKey<T : ExecutionContextElement>(
    val name: String,
    val type: Class<T>,
) {
    init {
        require(name.isNotBlank()) { "ExecutionContext key name must not be blank" }
    }
}

/**
 * Structurally immutable execution attribution. Element implementations are expected to be immutable values.
 */
class ExecutionContextSnapshot private constructor(
    private val values: Map<ExecutionContextKey<*>, ExecutionContextElement>,
) {
    val isEmpty: Boolean
        get() = values.isEmpty()

    val size: Int
        get() = values.size

    operator fun <T : ExecutionContextElement> get(key: ExecutionContextKey<T>): T? {
        val value = values[key] ?: return null
        return key.type.cast(value)
    }

    operator fun contains(key: ExecutionContextKey<*>): Boolean = values.containsKey(key)

    fun toBuilder(): Builder = Builder(values)

    internal fun entries(): List<Pair<ExecutionContextKey<*>, ExecutionContextElement>> =
        values.map { (key, value) -> key to value }

    override fun equals(other: Any?): Boolean =
        this === other || other is ExecutionContextSnapshot && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "ExecutionContextSnapshot(keys=${values.keys.map { it.name }.sorted()})"

    class Builder internal constructor(
        initialValues: Map<ExecutionContextKey<*>, ExecutionContextElement> = emptyMap(),
    ) {
        private val values = LinkedHashMap(initialValues)

        fun <T : ExecutionContextElement> put(key: ExecutionContextKey<T>, value: T): Builder {
            require(key.type.isInstance(value)) {
                "ExecutionContext value for '${key.name}' must be ${key.type.name}, got ${value.javaClass.name}"
            }
            check(values.keys.none { it.name == key.name }) {
                "ExecutionContext key '${key.name}' is already present; use replace() explicitly"
            }
            values[key] = value
            return this
        }

        fun <T : ExecutionContextElement> replace(key: ExecutionContextKey<T>, value: T): Builder {
            require(key.type.isInstance(value)) {
                "ExecutionContext value for '${key.name}' must be ${key.type.name}, got ${value.javaClass.name}"
            }
            val existingKey = values.keys.firstOrNull { it.name == key.name }
            check(existingKey == null || existingKey == key) {
                "ExecutionContext key '${key.name}' cannot change type from " +
                    "${existingKey?.type?.name} to ${key.type.name}"
            }
            values[key] = value
            return this
        }

        fun remove(key: ExecutionContextKey<*>): Builder {
            values.remove(key)
            return this
        }

        fun build(): ExecutionContextSnapshot =
            if (values.isEmpty()) EMPTY else ExecutionContextSnapshot(values.toMap())
    }

    companion object {
        @JvmField
        val EMPTY: ExecutionContextSnapshot = ExecutionContextSnapshot(emptyMap())

        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

fun interface ExecutionContextAccessor {
    fun current(): ExecutionContextSnapshot
}

fun interface ExecutionContextScopeManager {
    fun install(snapshot: ExecutionContextSnapshot): AutoCloseable
}
