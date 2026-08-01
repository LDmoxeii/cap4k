package com.only4.cap4k.ddd.core.domain.managed

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

interface ManagedFieldRegistry {
    val allBindings: List<ManagedFieldBinding>

    fun bindings(entityType: KClass<*>): List<ManagedFieldBinding>

    fun bindings(
        entityType: KClass<*>,
        lifecycle: ManagedFieldLifecycle,
    ): List<ManagedFieldBinding>

    fun handles(entity: Any): List<ManagedFieldHandle>

    fun handles(
        entity: Any,
        lifecycle: ManagedFieldLifecycle,
        qualifiers: Set<String>,
    ): List<ManagedFieldHandle>

    fun initializerFor(qualifier: String): ManagedEntityInitializer?

    fun mutationFootprint(
        entityType: KClass<*>,
        fieldName: String,
        policyKey: String,
    ): Set<String>
}

class DefaultManagedFieldRegistry(
    catalogs: Iterable<ManagedFieldCatalog>,
    initializers: Iterable<ManagedEntityInitializer> = emptyList(),
    adapters: Iterable<ManagedValueAdapter> = emptyList(),
    accessors: Iterable<ManagedFieldAccessor> = emptyList(),
) : ManagedFieldRegistry {
    private val initializersByQualifier = uniqueInitializers(initializers)
    private val adaptersByQualifier = uniqueAdapters(adapters)
    private val resolvedByEntityType: Map<KClass<*>, List<ResolvedManagedFieldBinding>>

    override val allBindings: List<ManagedFieldBinding>
        get() = resolvedByEntityType.values.flatten().map(ResolvedManagedFieldBinding::binding)

    init {
        val bindings = catalogs.flatMap(ManagedFieldCatalog::bindings)
        val accessorMap = uniqueAccessors(accessors)
        validateBindingIdentity(bindings)
        validateSlots(bindings)
        validateAdmissionOwners(bindings)
        validateRuntimeSupport(bindings)
        resolvedByEntityType = bindings
            .groupBy(ManagedFieldBinding::entityType)
            .mapValues { (_, entityBindings) ->
                entityBindings.map { binding ->
                    val key = AccessorKey(binding.entityType, binding.fieldName, binding.policyKey)
                    val accessor = accessorMap[key] ?: reflectiveAccessor(binding)
                    validateAccessor(binding, accessor)
                    val adapter = resolveAdapter(binding)
                    ResolvedManagedFieldBinding(binding, accessor, adapter)
                }
            }
    }

    override fun bindings(entityType: KClass<*>): List<ManagedFieldBinding> =
        resolvedBindingsFor(entityType).map(ResolvedManagedFieldBinding::binding)

    override fun bindings(
        entityType: KClass<*>,
        lifecycle: ManagedFieldLifecycle,
    ): List<ManagedFieldBinding> = bindings(entityType).filter { lifecycle in it.lifecycles }

    override fun handles(entity: Any): List<ManagedFieldHandle> = resolvedBindingsFor(entity::class)
        .map { resolved -> DefaultManagedFieldHandle(entity, resolved) }

    override fun handles(
        entity: Any,
        lifecycle: ManagedFieldLifecycle,
        qualifiers: Set<String>,
    ): List<ManagedFieldHandle> = resolvedBindingsFor(entity::class)
        .asSequence()
        .filter { lifecycle in it.binding.lifecycles }
        .filter { it.binding.handlerQualifier in qualifiers }
        .map { resolved -> DefaultManagedFieldHandle(entity, resolved) }
        .toList()

    private fun resolvedBindingsFor(runtimeType: KClass<*>): List<ResolvedManagedFieldBinding> {
        resolvedByEntityType[runtimeType]?.let { return it }
        val candidates = resolvedByEntityType.filterKeys { declared ->
            declared.java.isAssignableFrom(runtimeType.java)
        }
        if (candidates.isEmpty()) return emptyList()
        val mostSpecific = candidates.keys.filter { candidate ->
            candidates.keys.none { other ->
                candidate != other && candidate.java.isAssignableFrom(other.java)
            }
        }
        require(mostSpecific.size == 1) {
            "managed field bindings for runtime type ${runtimeType.qualifiedName} are ambiguous: " +
                mostSpecific.joinToString { it.qualifiedName.orEmpty() }
        }
        return candidates.getValue(mostSpecific.single())
    }

    override fun initializerFor(qualifier: String): ManagedEntityInitializer? =
        initializersByQualifier[qualifier]

    override fun mutationFootprint(
        entityType: KClass<*>,
        fieldName: String,
        policyKey: String,
    ): Set<String> = resolvedByEntityType[entityType].orEmpty()
        .singleOrNull { it.binding.fieldName == fieldName && it.binding.policyKey == policyKey }
        ?.accessor
        ?.mutationFootprint
        ?: error("managed field binding not found for ${entityType.qualifiedName}.$fieldName[$policyKey]")

    private fun validateBindingIdentity(bindings: List<ManagedFieldBinding>) {
        bindings.groupBy { it.entityType to it.fieldName }.forEach { (key, duplicates) ->
            require(duplicates.size == 1) {
                "duplicate managed field binding for ${key.first.qualifiedName}.${key.second}: " +
                    duplicates.joinToString { it.policyKey }
            }
        }
        bindings.groupBy { it.entityType to it.persistencePropertyName }.forEach { (key, duplicates) ->
            require(duplicates.size == 1) {
                "duplicate managed persistence property binding for ${key.first.qualifiedName}.${key.second}: " +
                    duplicates.joinToString { it.policyKey }
            }
        }
    }

    private fun validateSlots(bindings: List<ManagedFieldBinding>) {
        bindings.filter { it.handlerQualifier != null }
            .groupBy { it.entityType to requireNotNull(it.handlerQualifier) }
            .forEach { (key, group) ->
                if (group.size == 1) return@forEach
                require(group.all { it.handlerSlot != null }) {
                    "managed qualifier ${key.second} on ${key.first.qualifiedName} must use slots for every field"
                }
                val slots = group.map { requireNotNull(it.handlerSlot) }
                require(slots.toSet().size == slots.size) {
                    "managed qualifier ${key.second} on ${key.first.qualifiedName} has duplicate slots: $slots"
                }
            }
    }

    private fun validateAdmissionOwners(bindings: List<ManagedFieldBinding>) {
        bindings.filter { ManagedFieldLifecycle.ENTITY_ADMISSION in it.lifecycles }.forEach { binding ->
            val qualifier = requireNotNull(binding.handlerQualifier) {
                "admission-managed binding ${binding.label} requires a handler qualifier"
            }
            require(initializersByQualifier.containsKey(qualifier)) {
                "admission-managed binding ${binding.label} has no ManagedEntityInitializer for '$qualifier'"
            }
        }
    }

    private fun validateRuntimeSupport(bindings: List<ManagedFieldBinding>) {
        bindings.filter {
            it.explicitValue == ManagedExplicitValuePolicy.FORBID &&
                ManagedFieldLifecycle.ENTITY_ADMISSION !in it.lifecycles
        }.forEach { binding ->
            require(binding.runtimeSupport is ManagedFieldRuntimeSupport.ForbiddenExplicitValue) {
                "FORBID managed binding ${binding.label} requires provider placeholder runtime support"
            }
        }
    }

    private fun resolveAdapter(binding: ManagedFieldBinding): ManagedValueAdapter? {
        if (binding.targetType.javaObjectType.isAssignableFrom(binding.semanticValueType.javaObjectType)) return null
        val qualifier = requireNotNull(binding.valueAdapterQualifier) {
            "managed binding ${binding.label} requires an adapter from " +
                "${binding.semanticValueType.qualifiedName} to ${binding.targetType.qualifiedName}"
        }
        val adapter = requireNotNull(adaptersByQualifier[qualifier]) {
            "managed binding ${binding.label} references missing adapter '$qualifier'"
        }
        require(adapter.sourceType == binding.semanticValueType && adapter.supports(binding.targetType)) {
            "managed adapter '$qualifier' is incompatible with ${binding.label}: " +
                "${binding.semanticValueType.qualifiedName} -> ${binding.targetType.qualifiedName}"
        }
        return adapter
    }

    private fun uniqueInitializers(
        initializers: Iterable<ManagedEntityInitializer>,
    ): Map<String, ManagedEntityInitializer> = buildMap {
        initializers.forEach { initializer ->
            require(initializer.qualifiers.isNotEmpty()) {
                "ManagedEntityInitializer ${initializer.javaClass.name} must own at least one qualifier"
            }
            initializer.qualifiers.forEach { qualifier ->
                require(qualifier.isNotBlank()) {
                    "ManagedEntityInitializer ${initializer.javaClass.name} contains a blank qualifier"
                }
                val previous = putIfAbsent(qualifier, initializer)
                require(previous == null || previous === initializer) {
                    "duplicate ManagedEntityInitializer qualifier '$qualifier': " +
                        "${previous?.javaClass?.name} and ${initializer.javaClass.name}"
                }
            }
        }
    }

    private fun uniqueAdapters(adapters: Iterable<ManagedValueAdapter>): Map<String, ManagedValueAdapter> =
        buildMap {
            adapters.forEach { adapter ->
                require(adapter.qualifier.isNotBlank()) {
                    "ManagedValueAdapter ${adapter.javaClass.name} has a blank qualifier"
                }
                val previous = putIfAbsent(adapter.qualifier, adapter)
                require(previous == null || previous === adapter) {
                    "duplicate ManagedValueAdapter qualifier '${adapter.qualifier}': " +
                        "${previous?.javaClass?.name} and ${adapter.javaClass.name}"
                }
            }
        }

    private fun uniqueAccessors(
        accessors: Iterable<ManagedFieldAccessor>,
    ): Map<AccessorKey, ManagedFieldAccessor> = buildMap {
        accessors.forEach { accessor ->
            require(accessor.fieldName.isNotBlank() && accessor.policyKey.isNotBlank()) {
                "ManagedFieldAccessor ${accessor.javaClass.name} must declare field and policy identities"
            }
            require(accessor.mutationFootprint.isNotEmpty() && accessor.mutationFootprint.none(String::isBlank)) {
                "ManagedFieldAccessor ${accessor.javaClass.name} must declare a non-empty mutation footprint"
            }
            val key = AccessorKey(accessor.entityType, accessor.fieldName, accessor.policyKey)
            val previous = putIfAbsent(key, accessor)
            require(previous == null) {
                "duplicate ManagedFieldAccessor for ${accessor.entityType.qualifiedName}.${accessor.fieldName}"
            }
        }
    }

    private fun validateAccessor(binding: ManagedFieldBinding, accessor: ManagedFieldAccessor) {
        require(accessor.entityType == binding.entityType && accessor.fieldName == binding.fieldName &&
            accessor.policyKey == binding.policyKey) {
            "ManagedFieldAccessor identity does not match ${binding.label}"
        }
        require(accessor.mutationFootprint.isNotEmpty()) {
            "ManagedFieldAccessor for ${binding.label} has an empty mutation footprint"
        }
    }

    private fun reflectiveAccessor(binding: ManagedFieldBinding): ManagedFieldAccessor {
        val fields = generateSequence(binding.entityType.java as Class<*>?) { it.superclass }
            .flatMap { type -> type.declaredFields.asSequence() }
            .filter { it.name == binding.fieldName && !Modifier.isStatic(it.modifiers) }
            .toList()
        require(fields.size == 1) {
            "managed binding ${binding.label} requires exactly one matching field declaration, found " +
                fields.joinToString(prefix = "[", postfix = "]") { "${it.declaringClass.name}.${it.name}" }
        }
        val field = fields.single()
        require(field.trySetAccessible()) {
            "managed field ${binding.label} is inaccessible and has no custom accessor"
        }
        require(binding.targetType.javaObjectType.isAssignableFrom(field.type.kotlin.javaObjectType) &&
            field.type.kotlin.javaObjectType.isAssignableFrom(binding.targetType.javaObjectType)) {
            "managed field ${binding.label} type ${field.type.name} does not match ${binding.targetType.qualifiedName}"
        }
        return ReflectiveManagedFieldAccessor(binding, field)
    }

    private data class AccessorKey(
        val entityType: KClass<*>,
        val fieldName: String,
        val policyKey: String,
    )
}

private data class ResolvedManagedFieldBinding(
    val binding: ManagedFieldBinding,
    val accessor: ManagedFieldAccessor,
    val adapter: ManagedValueAdapter?,
)

private class ReflectiveManagedFieldAccessor(
    private val binding: ManagedFieldBinding,
    private val field: Field,
) : ManagedFieldAccessor {
    override val entityType: KClass<*> = binding.entityType
    override val fieldName: String = binding.fieldName
    override val policyKey: String = binding.policyKey
    override val mutationFootprint: Set<String> = setOf(binding.persistencePropertyName)

    override fun readRaw(entity: Any): Any? = field.get(entity)

    override fun writeRaw(entity: Any, value: Any?) {
        field.set(entity, value)
    }
}

private class DefaultManagedFieldHandle(
    private val entity: Any,
    private val resolved: ResolvedManagedFieldBinding,
) : ManagedFieldHandle {
    private val binding = resolved.binding

    override val fieldName: String = binding.fieldName
    override val persistencePropertyName: String = binding.persistencePropertyName
    override val policyKey: String = binding.policyKey
    override val handlerQualifier: String? = binding.handlerQualifier
    override val handlerSlot: String? = binding.handlerSlot
    override val semanticValueType: KClass<*> = binding.semanticValueType
    override val targetType: KClass<*> = binding.targetType
    override val nullable: Boolean = binding.nullable
    override val runtimeSupport: ManagedFieldRuntimeSupport? = binding.runtimeSupport
    override val mutationFootprint: Set<String> = resolved.accessor.mutationFootprint

    override fun readTarget(): Any? = resolved.accessor.readRaw(entity)

    override fun adaptSemantic(value: Any?): Any? {
        if (value == null) {
            require(nullable) { "managed field ${binding.label} is not nullable" }
            return null
        }
        require(semanticValueType.isInstance(value)) {
            "managed field ${binding.label} expected semantic value ${semanticValueType.qualifiedName}, " +
                "got ${value.javaClass.name}"
        }
        val adapted = resolved.adapter?.adapt(value, targetType) ?: value
        require(targetType.isInstance(adapted)) {
            "managed field ${binding.label} adapter '${binding.valueAdapterQualifier}' produced " +
                "${adapted.javaClass.name}, expected ${targetType.qualifiedName}"
        }
        return adapted
    }

    override fun matchesSemantic(value: Any?): Boolean = readTarget() == adaptSemantic(value)

    override fun assignSemantic(value: Any?) {
        resolved.accessor.writeRaw(entity, adaptSemantic(value))
    }
}
