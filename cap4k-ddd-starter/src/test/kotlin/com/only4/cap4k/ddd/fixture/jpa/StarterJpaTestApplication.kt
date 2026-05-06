package com.only4.cap4k.ddd.fixture.jpa

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriber
import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import com.only4.cap4k.ddd.application.persistence.ArchivedRequest
import com.only4.cap4k.ddd.application.persistence.ArchivedRequestJpaRepository
import com.only4.cap4k.ddd.application.persistence.Request
import com.only4.cap4k.ddd.application.persistence.RequestJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSaga
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaProcess
import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEvent
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.fixture.event.StarterEventScanMarker
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackageClasses = [StarterEventScanMarker::class])
@EntityScan(
    basePackageClasses = [
        Event::class,
        ArchivedEvent::class,
        Request::class,
        ArchivedRequest::class,
        Saga::class,
        SagaProcess::class,
        ArchivedSaga::class,
        ArchivedSagaProcess::class,
        EventHttpSubscriber::class,
    ]
)
@EnableJpaRepositories(
    basePackageClasses = [
        EventJpaRepository::class,
        ArchivedEventJpaRepository::class,
        RequestJpaRepository::class,
        ArchivedRequestJpaRepository::class,
        SagaJpaRepository::class,
        ArchivedSagaJpaRepository::class,
        EventHttpSubscriberJpaRepository::class,
    ]
)
class StarterJpaTestApplication
