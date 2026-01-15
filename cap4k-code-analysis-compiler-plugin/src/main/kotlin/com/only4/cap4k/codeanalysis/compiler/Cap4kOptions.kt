package com.only4.cap4k.codeanalysis.compiler

import com.only4.cap4k.codeanalysis.core.config.OptionsKeys
import java.nio.file.Path
import kotlin.io.path.Path

data class Cap4kOptions(
    val outputDir: Path = Path("build/cap4k-code-analysis"),
    val scanSpring: Boolean = true,
    val includeRepoUow: Boolean = true,
    val mediatorFq: String = "com.only4.cap4k.ddd.core.Mediator",
    val aggregateAnnFq: String = "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
    val domainEventAnnFq: String = "com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent",
    val integrationEventAnnFq: String = "com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent",
    val eventListenerAnnFq: String = "org.springframework.context.event.EventListener",
    val requestSupervisorFq: String = "com.only4.cap4k.ddd.core.application.RequestSupervisor",
    val unitOfWorkFq: String = "com.only4.cap4k.ddd.core.application.UnitOfWork",
    val repositorySupervisorFq: String = "com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor",
    val aggregateFactorySupervisorFq: String = "com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor",
    val requestParamFq: String = "com.only4.cap4k.ddd.core.application.RequestParam",
) {
    companion object {
        fun fromSystemProperties(): Cap4kOptions = Cap4kOptions(
            outputDir = System.getProperty(OptionsKeys.OUTPUT_DIR)?.let { Path(it) }
                ?: Path("build/cap4k-code-analysis"),
            scanSpring = System.getProperty(OptionsKeys.SCAN_SPRING)?.toBooleanStrictOrNull() ?: true,
            includeRepoUow = System.getProperty(OptionsKeys.INCLUDE_REPO_UOW)?.toBooleanStrictOrNull() ?: true,
            mediatorFq = System.getProperty(OptionsKeys.MEDIATOR_FQ)
                ?: "com.only4.cap4k.ddd.core.Mediator",
            aggregateAnnFq = System.getProperty(OptionsKeys.AGGREGATE_ANNOTATION_FQ)
                ?: "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            domainEventAnnFq = System.getProperty(OptionsKeys.DOMAIN_EVENT_ANNOTATION_FQ)
                ?: "com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent",
            integrationEventAnnFq = System.getProperty(OptionsKeys.INTEGRATION_EVENT_ANNOTATION_FQ)
                ?: "com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent",
            eventListenerAnnFq = System.getProperty(OptionsKeys.EVENT_LISTENER_ANNOTATION_FQ)
                ?: "org.springframework.context.event.EventListener",
            requestSupervisorFq = System.getProperty(OptionsKeys.REQUEST_SUPERVISOR_FQ)
                ?: "com.only4.cap4k.ddd.core.application.RequestSupervisor",
            unitOfWorkFq = System.getProperty(OptionsKeys.UNIT_OF_WORK_FQ)
                ?: "com.only4.cap4k.ddd.core.application.UnitOfWork",
            repositorySupervisorFq = System.getProperty(OptionsKeys.REPOSITORY_SUPERVISOR_FQ)
                ?: "com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor",
            aggregateFactorySupervisorFq = System.getProperty(OptionsKeys.AGG_FACTORY_SUPERVISOR_FQ)
                ?: "com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor",
            requestParamFq = System.getProperty(OptionsKeys.REQUEST_PARAM_FQ)
                ?: "com.only4.cap4k.ddd.core.application.RequestParam",
        )
    }
}
