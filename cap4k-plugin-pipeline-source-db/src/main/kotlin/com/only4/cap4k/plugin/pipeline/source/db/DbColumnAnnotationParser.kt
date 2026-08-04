package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.requireManagedFieldPolicyKey

internal object DbColumnAnnotationParser {
    private val supportedColumnAnnotations = setOf(
        "ParentRef",
        "Type",
        "RefAggregate",
        "RefId",
        "Managed",
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
        val managedPolicyKey = resolveManagedPolicyKey(annotations)
        val parentRef = hasMarker(
            annotations = annotations,
            name = "ParentRef",
            invalidValueMessage = "invalid @ParentRef annotation: explicit values are not supported.",
        )
        require(!(parentRef && (refAggregate != null || refId != null))) {
            "@ParentRef cannot be combined with @RefAggregate or @RefId."
        }
        require(refAggregate == null || refId == null) {
            "conflicting @RefAggregate and @RefId annotations on the same column comment."
        }
        return DbColumnAnnotationParseResult(
            typeBinding = typeBinding,
            enumItems = emptyList(),
            parentRef = parentRef,
            refAggregate = refAggregate,
            refId = refId,
            managedPolicyKey = managedPolicyKey,
            cleanedComment = DbCommentAnnotationParser.strip(comment, supportedColumnAnnotations),
        )
    }

    private fun rejectUnsupportedAnnotations(annotations: List<ParsedDbCommentAnnotation>) {
        val unsupported = annotations.firstOrNull { it.rawName !in supportedColumnAnnotations } ?: return
        throw IllegalArgumentException(
            "unsupported column annotation @${unsupported.rawName}. Supported column annotations: " +
                "@ParentRef, @Type, @RefAggregate, @RefId, @Managed=<policy-key>."
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

    private fun resolveManagedPolicyKey(annotations: List<ParsedDbCommentAnnotation>): String? {
        val matchingAnnotations = annotations.filter { it.rawName == "Managed" }
        require(matchingAnnotations.size <= 1) { "multiple @Managed annotations are not allowed." }
        if (matchingAnnotations.isEmpty()) {
            return null
        }

        val rawValue = matchingAnnotations.single().value
        require(rawValue.isNotBlank()) { "invalid @Managed annotation: value is required." }
        if (
            rawValue.trim().equals("identifier.snowflake", ignoreCase = true) ||
            rawValue.trim().equals("snowflake", ignoreCase = true)
        ) {
            throw IllegalArgumentException(
                "unsupported application-side Strong ID strategy: rejected value '$rawValue' at @Managed policy key; " +
                    "supported application-side strategy: uuid7",
            )
        }

        return requireManagedFieldPolicyKey(rawValue, "@Managed policy key")
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
    val managedPolicyKey: String? = null,
    val cleanedComment: String = "",
)
