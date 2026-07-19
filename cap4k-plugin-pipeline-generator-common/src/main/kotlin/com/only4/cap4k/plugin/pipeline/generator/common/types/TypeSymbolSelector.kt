package com.only4.cap4k.plugin.pipeline.generator.common.types

object TypeSymbolSelector {
    fun selectShortNameCandidates(
        candidates: List<TypeSymbolIdentity>,
        aggregateContext: List<String>,
    ): List<TypeSymbolIdentity> {
        val uniqueCandidates = candidates.distinctBy { it.fqcn }
        val singleAggregate = singleAggregateContext(aggregateContext)
        if (singleAggregate != null) {
            val localManifestCandidates = uniqueCandidates.filter { candidate ->
                candidate.manifestOwned &&
                    !candidate.shared &&
                    candidate.ownerAggregateName == singleAggregate
            }
            if (localManifestCandidates.isNotEmpty()) {
                return localManifestCandidates
            }
        }
        return uniqueCandidates
    }

    private fun singleAggregateContext(aggregateContext: List<String>): String? {
        val names = aggregateContext
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return names.singleOrNull()
    }
}
