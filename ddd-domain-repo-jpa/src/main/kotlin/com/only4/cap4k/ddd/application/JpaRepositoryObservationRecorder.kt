package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan

interface JpaRepositoryObservationRecorder {
    fun observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan)
}
