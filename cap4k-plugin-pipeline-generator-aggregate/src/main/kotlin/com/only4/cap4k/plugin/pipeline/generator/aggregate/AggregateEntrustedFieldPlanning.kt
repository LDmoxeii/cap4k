package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole

internal data class AggregateEntrustedFields(
    val databaseIdentityFieldName: String? = null,
    val versionFieldName: String? = null,
) {
    fun isDatabaseIdentity(name: String) = databaseIdentityFieldName == name

    fun isVersion(name: String) = versionFieldName == name

    fun isProviderAssigned(name: String) = isDatabaseIdentity(name) || isVersion(name)
}

internal object AggregateEntrustedFieldPlanning {
    fun resolve(entity: EntityModel, model: CanonicalModel): AggregateEntrustedFields {
        val resolvedPolicy = model.managedFieldPolicies.singleOrNull {
            it.entityName == entity.name && it.entityPackageName == entity.packageName
        } ?: return AggregateEntrustedFields()
        val idPolicyControl = model.aggregateIdPolicyControls.singleOrNull {
            it.entityName == entity.name && it.entityPackageName == entity.packageName
        }
        val providerControl = model.aggregatePersistenceProviderControls.singleOrNull {
            it.entityName == entity.name && it.entityPackageName == entity.packageName
        }
        val id = resolvedPolicy.requireIdentifier()
        val databaseIdentityFieldName = if (id.policyKey == "identifier.database-identity") {
            require(
                id.fieldName == entity.idField.name &&
                    idPolicyControl?.idFieldName == id.fieldName &&
                    idPolicyControl.kind == AggregateIdPolicyKind.DATABASE_SIDE
            ) {
                "resolved database identity projection mismatch for " +
                    "${entity.packageName}.${entity.name}.${id.fieldName}"
            }
            id.fieldName
        } else {
            null
        }
        val version = resolvedPolicy.fieldByRole(ManagedFieldRole.VERSION)
        val versionFieldName = if (version != null) {
            require(
                providerControl?.versionFieldName == version.fieldName
            ) {
                "resolved version projection mismatch for ${entity.packageName}.${entity.name}: " +
                    "resolved=${version.fieldName}, provider=${providerControl?.versionFieldName}"
            }
            version.fieldName
        } else {
            null
        }

        return AggregateEntrustedFields(
            databaseIdentityFieldName = databaseIdentityFieldName,
            versionFieldName = versionFieldName,
        )
    }
}
