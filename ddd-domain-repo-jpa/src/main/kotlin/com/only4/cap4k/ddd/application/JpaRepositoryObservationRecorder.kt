package com.only4.cap4k.ddd.application

interface JpaRepositoryObservationRecorder {
    fun observeRepositoryLoad(root: Any)
}
