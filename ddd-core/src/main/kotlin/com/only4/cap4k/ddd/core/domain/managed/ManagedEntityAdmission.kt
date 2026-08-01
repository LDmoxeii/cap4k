package com.only4.cap4k.ddd.core.domain.managed

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

enum class ManagedEntityAdmissionKind {
    AGGREGATE_ROOT,
    OWNED_CHILD,
}

data class ManagedEntityInitializationContext(
    val executionContext: ExecutionContextSnapshot,
)

interface ManagedEntityFieldSet : Iterable<ManagedFieldHandle> {
    val entityType: KClass<*>
}

interface ManagedEntityInitializer {
    val qualifiers: Set<String>

    fun initialize(
        admission: ManagedEntityAdmissionKind,
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    )

    fun validate(
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    )
}

interface ManagedEntityAdmissionCoordinator {
    fun admit(entity: Any, kind: ManagedEntityAdmissionKind)

    fun validate(entity: Any, executionContext: ExecutionContextSnapshot)

    companion object {
        @JvmField
        val NO_OP: ManagedEntityAdmissionCoordinator = object : ManagedEntityAdmissionCoordinator {
            override fun admit(entity: Any, kind: ManagedEntityAdmissionKind) = Unit
            override fun validate(entity: Any, executionContext: ExecutionContextSnapshot) = Unit
        }
    }
}

class DefaultManagedEntityAdmissionCoordinator(
    private val registry: ManagedFieldRegistry,
    private val executionContextAccessor: ExecutionContextAccessor,
) : ManagedEntityAdmissionCoordinator {
    private val admitted = WeakIdentityAdmissionSet()

    @Synchronized
    override fun admit(entity: Any, kind: ManagedEntityAdmissionKind) {
        val previous = admitted.record(entity, kind)
        check(previous == null || previous.kind == kind) {
            "managed entity ${entity.javaClass.name} was already admitted as ${previous?.kind} " +
                "and cannot be admitted as $kind"
        }
        if (previous != null) {
            validate(entity, executionContextAccessor.current())
            return
        }
        var completed = false
        try {
            process(
                entity = entity,
                context = ManagedEntityInitializationContext(executionContextAccessor.current()),
                initialize = true,
                kind = kind,
            )
            admitted.complete(entity, immutableAdmissionValues(entity))
            completed = true
        } finally {
            if (!completed) admitted.remove(entity)
        }
    }

    override fun validate(entity: Any, executionContext: ExecutionContextSnapshot) {
        process(
            entity = entity,
            context = ManagedEntityInitializationContext(executionContext),
            initialize = false,
            kind = null,
        )
        val admittedValues = admitted.values(entity) ?: return
        val currentValues = immutableAdmissionValues(entity)
        check(currentValues == admittedValues) {
            val changed = (currentValues.keys + admittedValues.keys)
                .filter { currentValues[it] != admittedValues[it] }
                .sorted()
            "managed entity ${entity.javaClass.name} changed immutable admission fields $changed"
        }
    }

    private fun process(
        entity: Any,
        context: ManagedEntityInitializationContext,
        initialize: Boolean,
        kind: ManagedEntityAdmissionKind?,
    ) {
        validateForbiddenExplicitValues(entity)
        val bindings = registry.bindings(entity::class, ManagedFieldLifecycle.ENTITY_ADMISSION)
        val qualifiers = bindings.mapNotNull(ManagedFieldBinding::handlerQualifier).toSet()
        qualifiers.sorted().forEach { qualifier ->
            val initializer = requireNotNull(registry.initializerFor(qualifier)) {
                "managed entity ${entity.javaClass.name} has no initializer for '$qualifier'"
            }
            val handles = registry.handles(
                entity = entity,
                lifecycle = ManagedFieldLifecycle.ENTITY_ADMISSION,
                qualifiers = setOf(qualifier),
            )
            val fields = DefaultManagedEntityFieldSet(entity::class, handles)
            if (initialize) initializer.initialize(requireNotNull(kind), context, fields)
            initializer.validate(context, fields.asReadOnly())
        }
    }

    private fun validateForbiddenExplicitValues(entity: Any) {
        val bindings = registry.bindings(entity::class)
            .filter {
                it.explicitValue == ManagedExplicitValuePolicy.FORBID &&
                    ManagedFieldLifecycle.ENTITY_ADMISSION !in it.lifecycles
            }
        if (bindings.isEmpty()) return
        val handles = registry.handles(entity).associateBy { it.fieldName to it.policyKey }
        bindings.forEach { binding ->
            val handle = requireNotNull(handles[binding.fieldName to binding.policyKey]) {
                "managed field handle is missing for ${binding.label}"
            }
            val support = requireNotNull(handle.runtimeSupport as? ManagedFieldRuntimeSupport.ForbiddenExplicitValue) {
                "managed field ${binding.label} has no provider placeholder runtime support"
            }
            check(support.isProviderPlaceholder(handle.readTarget())) {
                "managed field ${binding.fieldName}[${binding.policyKey}] forbids an explicit value before persistence"
            }
        }
    }

    private fun immutableAdmissionValues(entity: Any): Map<String, Any?> {
        val bindings = registry.bindings(entity::class, ManagedFieldLifecycle.ENTITY_ADMISSION)
            .filter { it.persistence.update == ManagedValueAuthority.NONE }
        if (bindings.isEmpty()) return emptyMap()
        val allowedKeys = bindings.mapTo(hashSetOf()) { it.fieldName to it.policyKey }
        val handles = registry.handles(
            entity,
            ManagedFieldLifecycle.ENTITY_ADMISSION,
            bindings.mapNotNull(ManagedFieldBinding::handlerQualifier).toSet(),
        )
        return handles
            .filter { (it.fieldName to it.policyKey) in allowedKeys }
            .associate { "${it.fieldName}[${it.policyKey}]" to it.readTarget() }
    }
}

object ManagedEntityAdmissionCoordinatorSupport {
    @Volatile
    private var configured: ManagedEntityAdmissionCoordinator? = null

    @JvmStatic
    fun configure(coordinator: ManagedEntityAdmissionCoordinator) {
        configured = coordinator
    }

    @JvmStatic
    fun admit(entity: Any, kind: ManagedEntityAdmissionKind) {
        configured?.admit(entity, kind)
    }

    @JvmStatic
    fun reset() {
        configured = null
    }
}

class StandardManagedEntityInitializer : ManagedEntityInitializer {
    override val qualifiers: Set<String> = setOf(
        "identifier.uuid7",
        "identifier.snowflake",
        "identifier.assigned",
        "soft-delete",
    )

    override fun initialize(
        admission: ManagedEntityAdmissionKind,
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    ) {
        fields.forEach { field ->
            when (val support = field.runtimeSupport) {
                is ManagedFieldRuntimeSupport.ApplicationIdentifier -> initializeIdentifier(field, support)
                is ManagedFieldRuntimeSupport.SoftDelete -> initializeSoftDelete(field, support)
                is ManagedFieldRuntimeSupport.ForbiddenExplicitValue -> error(
                    "built-in admission handler cannot own provider placeholder policy ${field.policyKey}",
                )
                null -> error("built-in managed field ${field.policyKey} has no generated runtime support")
            }
        }
    }

    override fun validate(
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    ) {
        fields.forEach { field ->
            when (val support = field.runtimeSupport) {
                is ManagedFieldRuntimeSupport.ApplicationIdentifier -> validateIdentifier(field, support)
                is ManagedFieldRuntimeSupport.SoftDelete -> check(field.matchesSemantic(support.activeSentinel)) {
                    "new entity managed field ${field.fieldName} must contain the active soft-delete sentinel"
                }
                is ManagedFieldRuntimeSupport.ForbiddenExplicitValue -> error(
                    "built-in admission handler cannot own provider placeholder policy ${field.policyKey}",
                )
                null -> error("built-in managed field ${field.policyKey} has no generated runtime support")
            }
        }
    }

    private fun initializeIdentifier(
        field: ManagedFieldHandle,
        support: ManagedFieldRuntimeSupport.ApplicationIdentifier,
    ) {
        val current = field.readTarget()
        if (!support.isAbsent(current)) {
            support.validateTarget(requireNotNull(current))
            return
        }
        val allocate = support.allocateTarget
        check(allocate != null) { "managed identifier ${field.fieldName} requires an explicit value" }
        field.assignSemantic(allocate())
    }

    private fun validateIdentifier(
        field: ManagedFieldHandle,
        support: ManagedFieldRuntimeSupport.ApplicationIdentifier,
    ) {
        val current = field.readTarget()
        check(!support.isAbsent(current)) { "managed identifier ${field.fieldName} is absent" }
        support.validateTarget(requireNotNull(current))
    }

    private fun initializeSoftDelete(
        field: ManagedFieldHandle,
        support: ManagedFieldRuntimeSupport.SoftDelete,
    ) {
        val current = field.readTarget()
        if (current == null) field.assignSemantic(support.activeSentinel)
    }
}

private class DefaultManagedEntityFieldSet(
    override val entityType: KClass<*>,
    private val handles: List<ManagedFieldHandle>,
) : ManagedEntityFieldSet, Iterable<ManagedFieldHandle> by handles {
    fun asReadOnly(): ManagedEntityFieldSet = DefaultManagedEntityFieldSet(
        entityType,
        handles.map(::ReadOnlyManagedFieldHandle),
    )
}

private class ReadOnlyManagedFieldHandle(
    private val delegate: ManagedFieldHandle,
) : ManagedFieldHandle by delegate {
    override fun assignSemantic(value: Any?): Nothing = error(
        "managed field ${delegate.fieldName}[${delegate.policyKey}] cannot be assigned during validation",
    )
}

private class WeakIdentityAdmissionSet {
    private val queue = ReferenceQueue<Any>()
    private val admissions = HashMap<IdentityWeakReference, AdmissionRecord>()

    @Synchronized
    fun record(entity: Any, kind: ManagedEntityAdmissionKind): AdmissionRecord? {
        drain()
        val lookup = IdentityWeakReference(entity)
        val previous = admissions[lookup]
        if (previous == null) {
            admissions[IdentityWeakReference(entity, queue)] = AdmissionRecord(kind)
        }
        return previous
    }

    @Synchronized
    fun complete(entity: Any, immutableValues: Map<String, Any?>) {
        drain()
        admissions.getValue(IdentityWeakReference(entity)).immutableValues = immutableValues.toMap()
    }

    @Synchronized
    fun values(entity: Any): Map<String, Any?>? {
        drain()
        return admissions[IdentityWeakReference(entity)]?.immutableValues
    }

    @Synchronized
    fun remove(entity: Any) {
        drain()
        admissions.remove(IdentityWeakReference(entity))
    }

    private fun drain() {
        while (true) {
            val reference = queue.poll() as IdentityWeakReference? ?: return
            admissions.remove(reference)
        }
    }
}

private data class AdmissionRecord(
    val kind: ManagedEntityAdmissionKind,
    var immutableValues: Map<String, Any?> = emptyMap(),
)

private class IdentityWeakReference(
    referent: Any,
    queue: ReferenceQueue<Any>? = null,
) : WeakReference<Any>(referent, queue) {
    private val identityHash = System.identityHashCode(referent)

    override fun hashCode(): Int = identityHash

    override fun equals(other: Any?): Boolean =
        this === other || other is IdentityWeakReference && identityHash == other.identityHash && get() === other.get()
}
