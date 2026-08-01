@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventSupervisor
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GeneratedDomainEventRuntimeContractTest {
    @AfterEach
    fun resetEventRuntime() = DefaultDomainEventSupervisor.reset()

    @Test
    fun `generated fact and marker events compile and satisfy the runtime historical fact boundary`() {
        val factSource = renderDomainEvent(
            typeName = "OrderCreatedDomainEvent",
            fields = listOf(
                mapOf(
                    "name" to "orderId",
                    "renderedType" to "String",
                    "nullable" to false,
                ),
            ),
        )
        val markerSource = renderDomainEvent(
            typeName = "OrderReconciledDomainEvent",
            fields = emptyList(),
        )

        assertFalse(factSource.contains("val entity:"))
        assertFalse(markerSource.contains("val entity:"))

        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("OrderCreatedDomainEvent.kt", factSource),
                SourceFile.kotlin("OrderReconciledDomainEvent.kt", markerSource),
            )
            inheritClassPath = true
            jvmTarget = "17"
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, compilation.exitCode, compilation.messages)

        val fact = compilation.classLoader
            .loadClass("com.acme.demo.domain.order.events.OrderCreatedDomainEvent")
            .getConstructor(String::class.java)
            .newInstance("order-42")
        val marker = compilation.classLoader
            .loadClass("com.acme.demo.domain.order.events.OrderReconciledDomainEvent")
            .getConstructor()
            .newInstance()
        val supervisor = DefaultDomainEventSupervisor(
            domainEventInterceptorManager = emptyInterceptorManager,
            eventSubscriberManager = noOpSubscriberManager,
        )

        assertDoesNotThrow {
            supervisor.attach(fact, Any())
            supervisor.attach(marker, Any())
        }
    }

    private fun renderDomainEvent(
        typeName: String,
        fields: List<Map<String, Any?>>,
    ): String = PebbleArtifactRenderer(
        PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList(),
        ),
    ).render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "domain-event",
                moduleRole = "domain",
                templateId = "design/domain_event.kt.peb",
                outputPath = "src/main/kotlin/com/acme/demo/domain/order/events/$typeName.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.domain.order.events",
                    "typeName" to typeName,
                    "description" to "generated historical fact",
                    "descriptionText" to "generated historical fact",
                    "descriptionCommentText" to "generated historical fact",
                    "descriptionKotlinStringLiteral" to "\"generated historical fact\"",
                    "persist" to false,
                    "imports" to emptyList<String>(),
                    "fields" to fields,
                    "nestedTypes" to emptyList<Map<String, Any?>>(),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
        config = ProjectConfig(),
    ).single().content

    private val emptyInterceptorManager = object : DomainEventInterceptorManager {
        override val orderedDomainEventInterceptors = emptySet<com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptor>()
        override val orderedEventInterceptors4DomainEvent = emptySet<com.only4.cap4k.ddd.core.domain.event.EventInterceptor>()
    }

    private val noOpSubscriberManager = object : EventSubscriberManager {
        override fun subscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean = true

        override fun unsubscribe(eventPayloadClass: Class<*>, subscriber: EventSubscriber<*>): Boolean = true

        override fun dispatch(eventPayload: Any) = Unit
    }
}
