package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel

internal object DbColumnAnnotationParser {
    private val supportedColumnAnnotations = setOf(
        "ParentRef",
        "Type",
        "RefAggregate",
        "RefId",
        "IdStrategy",
        "Managed",
        "Inherited",
    )

    fun parse(comment: String): DbColumnAnnotationParseResult {
        val annotations = DbCommentAnnotationParser.parse(comment)
        rejectUnsupportedAnnotations(annotations)

        val typeBinding = resolveRequiredValue(
            annotations = annotations,
            name = "Type",
            conflictMessage = "conflicting @Type annotations on the same column comment.",
            blankValueMessage = "blank @Type value is not allowed.",
            missingValueMessage = "missing value for @Type annotation.",
        )
        val refAggregate = resolveRequiredValue(
            annotations = annotations,
            name = "RefAggregate",
            conflictMessage = "conflicting @RefAggregate annotations on the same column comment.",
            blankValueMessage = "blank @RefAggregate value is not allowed.",
            missingValueMessage = "missing value for @RefAggregate annotation.",
        )
        val refId = resolveRequiredValue(
            annotations = annotations,
            name = "RefId",
            conflictMessage = "conflicting @RefId annotations on the same column comment.",
            blankValueMessage = "blank @RefId value is not allowed.",
            missingValueMessage = "missing value for @RefId annotation.",
        )
        val managedRole = resolveManagedRole(annotations)
        val idStrategy = resolveIdStrategy(annotations)
        val parentRef = hasMarker(
            annotations = annotations,
            name = "ParentRef",
            invalidValueMessage = "invalid @ParentRef annotation: explicit values are not supported.",
        )
        val inherited = hasMarker(
            annotations = annotations,
            name = "Inherited",
            invalidValueMessage = "invalid @Inherited annotation: explicit values are not supported.",
        )

        require(!(parentRef && (refAggregate != null || refId != null || idStrategy != null))) {
            "@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy."
        }
        require(refAggregate == null || refId == null) {
            "conflicting @RefAggregate and @RefId annotations on the same column comment."
        }
        require(!inherited || managedRole != null) {
            "@Inherited is valid only with @Managed=system, @Managed=scope, @Managed=deleted, or @Managed=version."
        }

        return DbColumnAnnotationParseResult(
            typeBinding = typeBinding,
            enumItems = emptyList(),
            parentRef = parentRef,
            refAggregate = refAggregate,
            refId = refId,
            idStrategy = idStrategy,
            managedRole = managedRole,
            inherited = if (inherited) true else null,
            cleanedComment = DbCommentAnnotationParser.strip(comment, supportedColumnAnnotations),
        )
    }

    private fun rejectUnsupportedAnnotations(annotations: List<ParsedDbCommentAnnotation>) {
        val unsupported = annotations.firstOrNull { it.rawName !in supportedColumnAnnotations } ?: return
        throw IllegalArgumentException(
            "unsupported column annotation @${unsupported.rawName}. Supported column annotations: " +
                "@ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity|uuid7, " +
                "@Managed=system|scope|deleted|version, @Inherited."
        )
    }

    private fun resolveRequiredValue(
        annotations: List<ParsedDbCommentAnnotation>,
        name: String,
        conflictMessage: String,
        blankValueMessage: String,
        missingValueMessage: String,
    ): String? {
        val matchingAnnotations = annotations.filter { it.rawName == name }
        if (matchingAnnotations.isEmpty()) {
            return null
        }

        require(matchingAnnotations.none { !it.hasExplicitValue }) { missingValueMessage }
        require(matchingAnnotations.none { it.value.isBlank() }) { blankValueMessage }

        val values = matchingAnnotations.map { it.value }.distinct()
        require(values.size <= 1) { conflictMessage }
        return values.single()
    }

    private fun resolveManagedRole(annotations: List<ParsedDbCommentAnnotation>): DbManagedRole? {
        val matchingAnnotations = annotations.filter { it.rawName == "Managed" }
        require(matchingAnnotations.size <= 1) { "multiple @Managed annotations are not allowed." }
        if (matchingAnnotations.isEmpty()) {
            return null
        }

        val rawValue = matchingAnnotations.single().value
        require(rawValue.isNotBlank()) { "invalid @Managed annotation: value is required." }

        return when (rawValue) {
            "system" -> DbManagedRole.SYSTEM
            "scope" -> DbManagedRole.SCOPE
            "deleted" -> DbManagedRole.DELETED
            "version" -> DbManagedRole.VERSION
            else -> throw IllegalArgumentException("unsupported @Managed value: $rawValue")
        }
    }

    private fun resolveIdStrategy(annotations: List<ParsedDbCommentAnnotation>): DbIdStrategy? {
        val values = annotations
            .filter { it.rawName == "IdStrategy" }
            .map { it.value }
            .distinct()
        if (values.isEmpty()) {
            return null
        }

        val rawValue = values.single()
        require(rawValue.isNotBlank()) { "invalid @IdStrategy annotation: value is required." }
        return when (rawValue.trim().lowercase()) {
            "db_identity" -> DbIdStrategy.DB_IDENTITY
            "uuid7" -> DbIdStrategy.UUID7
            else -> throw IllegalArgumentException("unsupported @IdStrategy value: $rawValue")
        }
    }

    private fun hasMarker(
        annotations: List<ParsedDbCommentAnnotation>,
        name: String,
        invalidValueMessage: String,
    ): Boolean {
        val matchingAnnotations = annotations.filter { it.rawName == name }
        require(matchingAnnotations.none { it.hasExplicitValue }) { invalidValueMessage }
        return matchingAnnotations.isNotEmpty()
    }
}

internal data class DbColumnAnnotationParseResult(
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val parentRef: Boolean = false,
    val refAggregate: String? = null,
    val refId: String? = null,
    val idStrategy: DbIdStrategy? = null,
    val managedRole: DbManagedRole? = null,
    val inherited: Boolean? = null,
    val cleanedComment: String = "",
)
