package com.only4.cap4k.ddd.application

internal data class JpaObservedIdentity(
    val entityType: Class<*>,
    val id: Any,
)

internal class JpaObservedEntity(
    val entity: Any,
    val identity: JpaObservedIdentity?,
) {
    override fun equals(other: Any?): Boolean =
        other is JpaObservedEntity && entity === other.entity

    override fun hashCode(): Int = System.identityHashCode(entity)
}

internal class JpaRepositoryObservationBaseline {
    private val observedByRoot = LinkedHashMap<ObjectIdentityKey, LinkedHashSet<JpaObservedEntity>>()
    private val rootKeyByObservedObject = LinkedHashMap<ObjectIdentityKey, ObjectIdentityKey>()
    private val observedByObject = LinkedHashMap<ObjectIdentityKey, JpaObservedEntity>()
    private val observedIdentities = LinkedHashSet<JpaObservedIdentity>()

    fun record(root: Any, entries: List<JpaObservedEntity>) {
        val rootKey = ObjectIdentityKey(root)
        val bucket = observedByRoot.getOrPut(rootKey) { LinkedHashSet() }
        entries.forEach { entry ->
            bucket += entry
            val entityKey = ObjectIdentityKey(entry.entity)
            rootKeyByObservedObject[entityKey] = rootKey
            observedByObject.putIfAbsent(entityKey, entry)
            entry.identity?.let { observedIdentities += it }
        }
    }

    fun entriesFor(root: Any): Set<JpaObservedEntity> =
        observedByRoot[rootKeyByObservedObject[ObjectIdentityKey(root)] ?: ObjectIdentityKey(root)].orEmpty()

    fun containsIdentity(identity: JpaObservedIdentity): Boolean =
        identity in observedIdentities

    fun identityFor(entity: Any): JpaObservedIdentity? =
        observedByObject[ObjectIdentityKey(entity)]?.identity

    fun isObservedObject(entity: Any): Boolean =
        rootKeyByObservedObject.containsKey(ObjectIdentityKey(entity))

    fun clear() {
        observedByRoot.clear()
        rootKeyByObservedObject.clear()
        observedByObject.clear()
        observedIdentities.clear()
    }
}
