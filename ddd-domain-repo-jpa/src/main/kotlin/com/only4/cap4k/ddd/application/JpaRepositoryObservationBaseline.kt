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
    private val observedRootKeyByObject = LinkedHashMap<ObjectIdentityKey, ObjectIdentityKey>()
    private val observedRootKeyByIdentity = LinkedHashMap<JpaObservedIdentity, ObjectIdentityKey>()
    private val observedChildRootKeyByIdentity = LinkedHashMap<JpaObservedIdentity, ObjectIdentityKey>()
    private val rootObjectByRootKey = LinkedHashMap<ObjectIdentityKey, Any>()
    private val observedByObject = LinkedHashMap<ObjectIdentityKey, JpaObservedEntity>()
    private val observedIdentities = LinkedHashSet<JpaObservedIdentity>()

    fun record(root: Any, entries: List<JpaObservedEntity>) {
        val canonicalRoot = entries.firstOrNull()?.entity ?: root
        val rootAliasKey = ObjectIdentityKey(root)
        val canonicalRootKey = ObjectIdentityKey(canonicalRoot)
        val existingRootKey = rootKeyByObservedObject[rootAliasKey]
            ?: rootKeyByObservedObject[canonicalRootKey]
            ?: entries.firstOrNull()?.identity?.let(observedRootKeyByIdentity::get)
        if (existingRootKey != null) {
            rootKeyByObservedObject[rootAliasKey] = existingRootKey
            rootKeyByObservedObject[canonicalRootKey] = existingRootKey
            observedRootKeyByObject[rootAliasKey] = existingRootKey
            observedRootKeyByObject[canonicalRootKey] = existingRootKey
            rootObjectByRootKey.putIfAbsent(
                existingRootKey,
                observedByRoot[existingRootKey]?.firstOrNull()?.entity ?: canonicalRoot,
            )
            observedByRoot[existingRootKey]
                ?.firstOrNull()
                ?.let { observedByObject.putIfAbsent(rootAliasKey, it) }
            return
        }

        val rootKey = canonicalRootKey
        val bucket = LinkedHashSet<JpaObservedEntity>()
        observedByRoot[rootKey] = bucket
        rootKeyByObservedObject[rootAliasKey] = rootKey
        rootKeyByObservedObject[canonicalRootKey] = rootKey
        observedRootKeyByObject[rootAliasKey] = rootKey
        observedRootKeyByObject[canonicalRootKey] = rootKey
        rootObjectByRootKey.putIfAbsent(rootKey, canonicalRoot)
        entries.forEachIndexed { index, entry ->
            bucket += entry
            val entityKey = ObjectIdentityKey(entry.entity)
            rootKeyByObservedObject[entityKey] = rootKey
            observedByObject.putIfAbsent(entityKey, entry)
            entry.identity?.let { identity ->
                observedIdentities += identity
                if (index == 0) {
                    observedRootKeyByIdentity.putIfAbsent(identity, rootKey)
                } else {
                    observedChildRootKeyByIdentity.putIfAbsent(identity, rootKey)
                }
            }
        }
        bucket.firstOrNull()?.let { observedByObject.putIfAbsent(rootAliasKey, it) }
    }

    fun entriesFor(root: Any, identity: JpaObservedIdentity? = null): Set<JpaObservedEntity> =
        observedByRoot[observedRootKeyFor(root, identity) ?: ObjectIdentityKey(root)].orEmpty()

    fun containsIdentity(identity: JpaObservedIdentity): Boolean =
        identity in observedIdentities

    fun identityFor(entity: Any): JpaObservedIdentity? =
        observedByObject[ObjectIdentityKey(entity)]?.identity

    fun isObservedRoot(entity: Any, identity: JpaObservedIdentity? = null): Boolean =
        observedRootKeyByObject.containsKey(ObjectIdentityKey(entity)) ||
            (identity != null && observedRootKeyByIdentity.containsKey(identity))

    fun isObservedChild(entity: Any, identity: JpaObservedIdentity? = null): Boolean {
        val key = ObjectIdentityKey(entity)
        return (rootKeyByObservedObject.containsKey(key) ||
            (identity != null && observedChildRootKeyByIdentity.containsKey(identity))) &&
            !isObservedRoot(entity, identity)
    }

    fun observedRootFor(entity: Any, identity: JpaObservedIdentity? = null): Any? =
        observedRootKeyFor(entity, identity)?.let(rootObjectByRootKey::get)

    fun observedRootForChild(entity: Any, identity: JpaObservedIdentity? = null): Any? =
        if (isObservedChild(entity, identity)) observedRootFor(entity, identity) else null

    fun isObservedObject(entity: Any, identity: JpaObservedIdentity? = null): Boolean =
        observedRootKeyFor(entity, identity) != null

    fun hasBaselineFor(root: Any, identity: JpaObservedIdentity? = null): Boolean =
        entriesFor(root, identity).isNotEmpty()

    private fun observedRootKeyFor(
        entity: Any,
        identity: JpaObservedIdentity?,
    ): ObjectIdentityKey? =
        rootKeyByObservedObject[ObjectIdentityKey(entity)]
            ?: identity?.let(observedRootKeyByIdentity::get)
            ?: identity?.let(observedChildRootKeyByIdentity::get)

    fun clear() {
        observedByRoot.clear()
        rootKeyByObservedObject.clear()
        observedByObject.clear()
        observedIdentities.clear()
        observedRootKeyByObject.clear()
        observedRootKeyByIdentity.clear()
        observedChildRootKeyByIdentity.clear()
        rootObjectByRootKey.clear()
    }
}
