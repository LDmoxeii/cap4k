@file:Suppress("DEPRECATION")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance
import java.nio.file.Path

class AnalysisOutputCorrectnessTest {
    @Test
    fun `event names preserve runtime literals and never fall back across metadata`() {
        val runtimeWhitespaceMessages = compileWithCap4kPluginExpectingFailure(
            eventContractSources(
                fileName = "WhitespaceDomainEvent.kt",
                declaration = """
                    package demo.domain.events

                    @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                        tag = "domain_event",
                        packageName = "orders.events",
                        name = "WhitespaceDomainEvent",
                        eventName = " order.created ",
                        family = "domain-event",
                    )
                    @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(
                        value = " order.created ",
                        persist = true,
                    )
                    data class WhitespaceDomainEvent(val orderId: Long)
                """.trimIndent(),
            ),
        )
        val metadataMissingMessages = compileWithCap4kPluginExpectingFailure(
            eventContractSources(
                fileName = "RuntimeOnlyDomainEvent.kt",
                declaration = """
                    package demo.domain.events

                    @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                        tag = "domain_event",
                        packageName = "orders.events",
                        name = "RuntimeOnlyDomainEvent",
                        family = "domain-event",
                    )
                    @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(
                        value = "order.runtime-only",
                        persist = true,
                    )
                    data class RuntimeOnlyDomainEvent(val orderId: Long)
                """.trimIndent(),
            ),
        )

        assertTrue(runtimeWhitespaceMessages.contains("domain-event metadata/runtime eventName conflict"))
        assertTrue(metadataMissingMessages.contains("domain-event metadata/runtime eventName conflict"))
    }

    @Test
    fun `integration event direction comes from design metadata while runtime annotation carries only event name`() {
        val json = compileDesignElements(
            eventContractSources(
                fileName = "OutboundIntegrationEvent.kt",
                declaration = """
                    package demo.application.events
                    @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                        tag = "integration_event", packageName = "orders.events",
                        name = "OutboundIntegrationEvent", eventName = "order.exported",
                        family = "integration-event", variant = "outbound",
                    )
                    @com.only4.cap4k.contract.IntegrationEvent("order.exported")
                    data class OutboundIntegrationEvent(val orderId: Long)
                """.trimIndent(),
            ),
        )
        assertTrue(json.contains("\"variant\":\"outbound\""), json)
    }

    @Test
    fun `transient domain event accepts two empty names without synthesizing metadata`() {
        val json = compileDesignElements(
            eventContractSources(
                fileName = "TransientDomainEvent.kt",
                declaration = """
                    package demo.domain.events

                    @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                        tag = "domain_event",
                        packageName = "orders.events",
                        name = "TransientDomainEvent",
                        family = "domain-event",
                    )
                    @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
                    data class TransientDomainEvent(val orderId: Long)
                """.trimIndent(),
            ),
        )

        assertTrue(json.contains("\"name\":\"TransientDomainEvent\""), json)
        assertTrue(json.contains("\"persist\":false"), json)
        assertTrue(!json.contains("\"eventName\""), json)
    }

    @Test
    fun `form feed default is recovered as a Kotlin supported unicode literal`() {
        val json = compileDesignElements(
            eventContractSources(
                fileName = "FormFeedPayload.kt",
                declaration = """
                    package demo.application.api

                    @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                        tag = "api",
                        packageName = "orders.api",
                        name = "FormFeedPayload",
                        family = "api-payload",
                        variant = "request",
                    )
                    data class FormFeedPayload(val marker: String = "\u000c")
                """.trimIndent(),
            ),
        )

        assertTrue(json.contains("\"defaultValue\":\"\\\"\\\\u000c\\\"\""), json)
    }

    @Test
    fun `command handler calling top level aggregate behavior extension emits exact entity method edges`() {
        val rels = compileRelationships(
            categorySources(
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        assertMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            eventId = "demo.domain.aggregates.category.events.CategorySortChanged",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `command handler calling cross module top level aggregate behavior extension emits exact entity method edges`() {
        val domainOutput = compileLibrary(
            categoryDomainLibrarySources(
                """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        val rels = compileRelationships(
            categoryAppSources(useTopLevelBehavior = true),
            classpaths = listOf(domainOutput),
        )

        assertCrossModuleMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `aggregate element fails fast for blank aggregate identity`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            categorySources(
                useTopLevelBehavior = true,
                categoryBody = """
                    @AggregateElementMetadata(
                        aggregate = " ",
                        type = "entity",
                        name = "Category",
                        packageName = "demo.domain.aggregates.category",
                        root = true,
                    )
                    class Category
                """.trimIndent(),
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent(),
            )
        )

        assertTrue(
            messages.contains("AggregateElementMetadata annotation on demo.domain.aggregates.category.Category must declare non-blank aggregate"),
        )
    }

    @Test
    fun `aggregate element fails fast for blank type`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            categorySources(
                useTopLevelBehavior = true,
                categoryBody = """
                    @AggregateElementMetadata(
                        aggregate = "Category",
                        type = " ",
                        name = "Category",
                        packageName = "demo.domain.aggregates.category",
                        root = true,
                    )
                    class Category
                """.trimIndent(),
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent(),
            )
        )

        assertTrue(
            messages.contains("AggregateElementMetadata annotation on demo.domain.aggregates.category.Category must declare non-blank type"),
        )
    }

    @Test
    fun `aggregate element fails fast for unknown type`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            categorySources(
                useTopLevelBehavior = true,
                categoryBody = """
                    @AggregateElementMetadata(
                        aggregate = "Category",
                        type = "unknown",
                        name = "Category",
                        packageName = "demo.domain.aggregates.category",
                        root = true,
                    )
                    class Category
                """.trimIndent(),
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent(),
            )
        )

        assertTrue(
            messages.contains("AggregateElementMetadata annotation on demo.domain.aggregates.category.Category has unsupported type: unknown"),
        )
    }

    @Test
    fun `aggregate element rejects retired generated structure types`() {
        listOf(
            "specification",
            "unique-query",
            "unique-query-handler",
            "unique-validator",
        ).forEach { retiredType ->
            val messages = compileWithCap4kPluginExpectingFailure(
                categorySources(
                    useTopLevelBehavior = true,
                    categoryBody = """
                        @AggregateElementMetadata(
                            aggregate = "Category",
                            type = "$retiredType",
                            name = "Category",
                            packageName = "demo.domain.aggregates.category",
                            root = true,
                        )
                        class Category
                    """.trimIndent(),
                    behaviorBody = """
                        fun Category.changeSort(sort: Int) {
                            CategorySortChanged(sort)
                        }
                    """.trimIndent(),
                )
            )

            assertTrue(
                messages.contains(
                    "AggregateElementMetadata annotation on demo.domain.aggregates.category.Category has unsupported type: $retiredType"
                ),
            )
        }
    }
    @Test
    fun `aggregate element accepts projection type without aggregate node`() {
        val rels = compileRelationships(
            categorySources(
                useTopLevelBehavior = true,
                categoryBody = """
                    @AggregateElementMetadata(
                        aggregate = "Category",
                        type = "projection",
                        name = "CategoryView",
                        packageName = "demo.domain.aggregates.category",
                    )
                    class Category
                """.trimIndent(),
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent(),
            )
        )

        assertTrue(
            rels.none { it.fromId == "demo.domain.aggregates.category.Category" || it.toId == "demo.domain.aggregates.category.Category" },
        )
    }

    @Test
    fun `repository carrier metadata is recovered as aggregate structure without becoming design json`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "AggregateElementMetadata.kt",
                    """
                        package com.only4.cap4k.analysis.metadata

                        annotation class AggregateElementMetadata(
                            val aggregate: String,
                            val name: String = "",
                            val packageName: String = "",
                            val description: String = "",
                            val type: String,
                            val root: Boolean = false,
                        )
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "OrderJpaRepositoryAdapter.kt",
                    """
                        package demo.adapter.domain.repositories

                        import com.only4.cap4k.analysis.metadata.AggregateElementMetadata

                        @AggregateElementMetadata(
                            aggregate = "Order",
                            name = "OrderRepository",
                            packageName = "demo.adapter.domain.repositories",
                            description = "Order repository carrier",
                            type = "repository",
                        )
                        internal class OrderJpaRepositoryAdapter
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(
            """[{"carrierQualifiedName":"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter","aggregate":"Order","name":"OrderRepository","packageName":"demo.adapter.domain.repositories","description":"Order repository carrier","type":"repository","root":false}]""",
            outputDir.resolve("aggregate-elements.json").toFile().readText(),
        )
        assertEquals("[]", outputDir.resolve("design-elements.json").toFile().readText())
    }

    @Test
    fun `repository carrier without aggregate metadata is reported as incomplete analysis evidence`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Repository.kt",
                    """
                        package com.only4.cap4k.ddd.core.domain.repo

                        interface Repository<ENTITY : Any>
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "RepositoryStereotype.kt",
                    """
                        package org.springframework.stereotype

                        @Target(AnnotationTarget.CLASS)
                        annotation class Repository
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "OrderJpaRepositoryAdapter.kt",
                    """
                        package demo.adapter.domain.repositories

                        import com.only4.cap4k.ddd.core.domain.repo.Repository as Cap4kRepository
                        import org.springframework.stereotype.Repository

                        @Repository
                        internal class OrderJpaRepositoryAdapter : Cap4kRepository<Any>
                    """.trimIndent(),
                ),
            ),
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        assertTrue(
            nodes.contains(
                "\"fullName\":\"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter\"," +
                    "\"type\":\"repository\",\"missingMetadata\":[" +
                    "\"com.only4.cap4k.analysis.metadata.AggregateElementMetadata\"]," +
                    "\"metadataOwner\":\"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter\""
            ),
            nodes,
        )
        assertEquals("[]", outputDir.resolve("aggregate-elements.json").toFile().readText())
    }

    @Test
    fun `top level behavior on aggregate annotated generated style entity without application side id keeps exact domain event edge`() {
        val rels = compileRelationships(
            categorySources(
                categoryBody = GENERATED_STYLE_CATEGORY_BODY,
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        assertMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            eventId = "demo.domain.aggregates.category.events.CategorySortChanged",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `command handler calling cross module top level behavior extension on aggregate annotated generated style entity without application side id emits exact entity method edges`() {
        val domainOutput = compileLibrary(
            categoryDomainLibrarySources(
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent(),
                categoryBody = GENERATED_STYLE_CATEGORY_BODY,
            )
        )

        val rels = compileRelationships(
            categoryAppSources(useTopLevelBehavior = true),
            classpaths = listOf(domainOutput),
        )

        assertCrossModuleMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `non aggregate annotated jpa entity in aggregate package is not inferred as aggregate entity`() {
        val rels = compileRelationships(
            categorySources(
                categoryBody = GENERATED_STYLE_CATEGORY_BODY_WITHOUT_AGGREGATE_ANNOTATION,
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        assertEquals(
            0,
            rels.count { it.type == "CommandHandlerToEntityMethod" && it.toId == "demo.domain.aggregates.category.Category::changeSort" },
        )
        assertEquals(
            0,
            rels.count { it.type == "AggregateToEntityMethod" && it.toId == "demo.domain.aggregates.category.Category::changeSort" },
        )
    }

    @Test
    fun `analyzer records missing aggregate metadata on unannotated jpa symbol`() {
        val outputDir = compileWithCap4kPlugin(
            categorySources(
                categoryBody = GENERATED_STYLE_CATEGORY_BODY_WITHOUT_AGGREGATE_ANNOTATION,
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        assertTrue(nodes.contains("\"fullName\":\"demo.domain.aggregates.category.Category\""))
        assertTrue(
            nodes.contains(
                "\"missingMetadata\":[\"com.only4.cap4k.analysis.metadata.AggregateElementMetadata\"]"
            )
        )
    }

    @Test
    fun `analyzer records missing design metadata on command and event symbols`() {
        val outputDir = compileWithCap4kPlugin(
            categorySources(
                categoryBody = DEFAULT_CATEGORY_BODY,
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        assertTrue(
            nodes.contains(
                "\"fullName\":\"demo.application.commands.category.UpdateCategorySortCmd\"," +
                    "\"type\":\"command\",\"missingMetadata\":[" +
                    "\"com.only4.cap4k.analysis.metadata.DesignBlockMetadata\"]," +
                    "\"metadataOwner\":\"demo.application.commands.category.UpdateCategorySortCmd\""
            ),
            nodes,
        )
        assertTrue(
            nodes.contains(
                "\"fullName\":\"demo.domain.aggregates.category.events.CategorySortChanged\"," +
                    "\"type\":\"domainevent\",\"missingMetadata\":[" +
                    "\"com.only4.cap4k.analysis.metadata.DesignBlockMetadata\"]," +
                    "\"metadataOwner\":\"demo.domain.aggregates.category.events.CategorySortChanged\""
            ),
            nodes,
        )
    }

    @Test
    fun `analyzer records missing design metadata on standalone application contracts`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Command.kt",
                    """
                        package com.only4.cap4k.ddd.core.application.command

                        interface Command<RESULT : Any>
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Query.kt",
                    """
                        package com.only4.cap4k.ddd.core.application.query

                        interface Query<RESULT : Any>
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "CapabilityCall.kt",
                    """
                        package com.only4.cap4k.ddd.core.application.capability

                        interface CapabilityCall<RESULT : Any>
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "StandaloneContracts.kt",
                    """
                        package demo.application.contracts

                        import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
                        import com.only4.cap4k.ddd.core.application.command.Command
                        import com.only4.cap4k.ddd.core.application.query.Query

                        object StandaloneCmd {
                            class Request : Command<Response>
                            class Response
                        }

                        object StandaloneQry {
                            class Request : Query<Response>
                            class Response
                        }

                        object StandaloneCapability {
                            class Request : CapabilityCall<Response>
                            class Response
                        }
                    """.trimIndent(),
                ),
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        mapOf(
            "demo.application.contracts.StandaloneCmd.Request" to "demo.application.contracts.StandaloneCmd",
            "demo.application.contracts.StandaloneQry.Request" to "demo.application.contracts.StandaloneQry",
            "demo.application.contracts.StandaloneCapability.Request" to "demo.application.contracts.StandaloneCapability",
        ).forEach { (symbol, owner) ->
            assertTrue(
                nodes.contains(
                    "\"fullName\":\"$symbol\"," +
                        "\"type\":" + when {
                            "Cmd" in owner -> "\"command\""
                            "Qry" in owner -> "\"query\""
                            else -> "\"capability\""
                        } +
                        ",\"missingMetadata\":[\"com.only4.cap4k.analysis.metadata.DesignBlockMetadata\"]," +
                        "\"metadataOwner\":\"$owner\""
                ),
                nodes,
            )
        }
    }

    @Test
    fun `command handler calling aggregate member method keeps exact entity method edges`() {
        val rels = compileRelationships(
            categorySources(
                categoryBody = """
                    @com.only4.cap4k.analysis.metadata.AggregateElementMetadata(
                        aggregate = "Category",
                        type = "entity",
                        name = "Category",
                        packageName = "demo.domain.aggregates.category",
                        root = true,
                    )
                    class Category {
                        fun changeSort(sort: Int) {
                            CategorySortChanged(sort)
                        }
                    }
                """.trimIndent(),
                useTopLevelBehavior = false,
                behaviorBody = ""
            )
        )

        assertMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            eventId = "demo.domain.aggregates.category.events.CategorySortChanged",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `supported stable defaults survive building block projection into design-elements json`() {
        val json = compileDesignElements(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
            ),
        )

        val issueCaptcha = findDesignElement(json, tag = "command", name = "IssueCaptcha")

        assertTrue(issueCaptcha.contains(""""name":"note","type":"String?","defaultValue":"null""""))
        assertTrue(issueCaptcha.contains(""""name":"title","type":"String","defaultValue":"\"inline\"""""))
        assertTrue(issueCaptcha.contains(""""name":"attempt","type":"Int","defaultValue":"1""""))
        assertTrue(issueCaptcha.contains(""""name":"enabled","type":"Boolean","defaultValue":"true""""))
        assertTrue(issueCaptcha.contains(""""name":"tags","type":"List<String>","defaultValue":"emptyList()""""))
        assertTrue(
            issueCaptcha.contains(
                """"name":"channels","type":"Set<demo.application.commands.auth.CaptchaChannel>","defaultValue":"emptySet()"""",
            ),
        )
        assertTrue(issueCaptcha.contains(""""name":"metadata","type":"Map<String,String>","defaultValue":"emptyMap()""""))
        assertTrue(issueCaptcha.contains(""""name":"preferredChannel","type":"demo.application.commands.auth.CaptchaChannel","defaultValue":"demo.application.commands.auth.CaptchaChannel.INLINE""""))
        assertTrue(issueCaptcha.contains(""""name":"policy","type":"demo.application.commands.auth.CaptchaPolicy","defaultValue":"demo.application.commands.auth.CaptchaPolicy""""))
        assertTrue(issueCaptcha.contains(""""name":"referenceTitle","type":"String","defaultValue":"demo.application.shared.defaults.SHARED_FIELD_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"externalPreferredChannel","type":"demo.application.shared.defaults.SharedCaptchaChannel","defaultValue":"demo.application.shared.defaults.SharedCaptchaChannel.IMAGE""""))
        assertTrue(issueCaptcha.contains(""""name":"externalPolicy","type":"demo.application.shared.defaults.SharedCaptchaPolicy","defaultValue":"demo.application.shared.defaults.SharedCaptchaPolicy""""))
        assertTrue(issueCaptcha.contains(""""name":"topLevelReferenceTitle","type":"String","defaultValue":"demo.application.shared.defaults.TOP_LEVEL_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"topLevelGetterReferenceTitle","type":"String","defaultValue":"demo.application.shared.defaults.TOP_LEVEL_GETTER_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"objectGetterReferenceTitle","type":"String","defaultValue":"demo.application.shared.defaults.SharedGetterDefaults.OBJECT_DEFAULT_TITLE""""))
    }

    @Test
    fun `unsupported default expressions fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "List<String>",
                channelsDefaultExpression = """listOf("inline")""",
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field channels"),
        )
    }

    @Test
    fun `non constant property references fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                referenceTitleDefaultExpression = "CaptchaDefaults.dynamicTitle",
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field referenceTitle"),
        )
    }

    @Test
    fun `private object backed getter references fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                privateReferenceTitleDefaultExpression = "PrivateCaptchaDefaults.PRIVATE_OBJECT_DEFAULT_TITLE",
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field privateReferenceTitle"),
        )
    }

    @Test
    fun `instance backed property references fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                referenceTitleDefaultExpression = "StableInstanceDefaults().defaultTitle",
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field referenceTitle"),
        )
    }

    @Test
    fun `private object defaults fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                extraRequestFields = """
                    val privatePolicy: Any = PrivateCaptchaPolicy,
                """.trimIndent(),
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field privatePolicy"),
        )
    }

    @Test
    fun `private enum defaults fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                extraRequestFields = """
                    val privatePreferredChannel: Any = PrivateCaptchaChannel.SMS,
                """.trimIndent(),
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field privatePreferredChannel"),
        )
    }

    @Test
    fun `multi statement composite defaults fail request projection explicitly`() {
        val collector = DesignElementCollector(Cap4kOptions())
        val param = irValueParameterWithDefault(
            name = "smuggledTitle",
            expression = irCompositeExpression(
                irIntConst(1),
                irStringConst("inline"),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            invokeResolveDefaultValue(
                collector = collector,
                param = param,
                context = "command IssueCaptcha request field smuggledTitle",
            )
        }

        assertEquals(
            "unsupported defaultValue expression for command IssueCaptcha request field smuggledTitle",
            error.message,
        )
    }

    @Test
    fun `multi statement block defaults fail request projection explicitly`() {
        val collector = DesignElementCollector(Cap4kOptions())
        val param = irValueParameterWithDefault(
            name = "smuggledBlockTitle",
            expression = irBlockExpression(
                irIntConst(1),
                irStringConst("inline"),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            invokeResolveDefaultValue(
                collector = collector,
                param = param,
                context = "command IssueCaptcha request field smuggledBlockTitle",
            )
        }

        assertEquals(
            "unsupported defaultValue expression for command IssueCaptcha request field smuggledBlockTitle",
            error.message,
        )
    }

    @Test
    fun `multi statement composite backed field initializers are not treated as stable constants`() {
        val collector = DesignElementCollector(Cap4kOptions())
        val field = irFieldWithInitializer(
            irCompositeExpression(
                irIntConst(1),
                irStringConst("inline"),
            ),
        )

        assertEquals(false, invokeIsStableConstantField(collector, field))
    }

    @Test
    fun `dynamic top level jvm field references fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                extraRequestFields = """
                    val dynamicTopLevelFieldTitle: String = DYNAMIC_TOP_LEVEL_DEFAULT_TITLE,
                """.trimIndent(),
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field dynamicTopLevelFieldTitle"),
        )
    }

    @Test
    fun `dynamic public static final field references fail request projection explicitly`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
                extraRequestFields = """
                    val dynamicJavaFieldTitle: String = CaptchaStableDefaults.DYNAMIC_TITLE,
                """.trimIndent(),
            ),
        )

        assertTrue(
            messages.contains("unsupported defaultValue expression for command IssueCaptcha field dynamicJavaFieldTitle"),
        )
    }

    private fun eventContractSources(
        fileName: String,
        declaration: String,
    ): List<SourceFile> = listOf(
        SourceFile.kotlin(
            "DesignBlockMetadata.kt",
            """
                package com.only4.cap4k.analysis.metadata

                annotation class DesignBlockMetadata(
                    val tag: String,
                    val name: String,
                    val packageName: String,
                    val description: String = "",
                    val aggregates: Array<String> = [],
                    val eventName: String = "",
                    val family: String = "",
                    val variant: String = "",
                )
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "DomainEvent.kt",
            """
                package com.only4.cap4k.ddd.core.domain.event.annotation

                annotation class DomainEvent(
                    val value: String = "",
                    val persist: Boolean = false,
                )
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "IntegrationEvent.kt",
            """
                package com.only4.cap4k.contract

                annotation class IntegrationEvent(
                    val value: String = "",
                )
            """.trimIndent(),
        ),
        SourceFile.kotlin(fileName, declaration),
    )

    private fun compileRelationships(
        sources: List<SourceFile>,
        classpaths: List<java.io.File> = emptyList(),
    ): List<RelationshipView> {
        val outputDir = compileWithCap4kPlugin(sources, classpaths)
        return readRelationships(outputDir)
    }

    private fun compileDesignElements(sources: List<SourceFile>): String {
        val outputDir = compileWithCap4kPlugin(sources)
        return outputDir.resolve("design-elements.json").toFile().readText()
    }

    private fun readRelationships(outputDir: Path): List<RelationshipView> {
        val json = outputDir.resolve("rels.json").toFile().readText()
        if (json == "[]") return emptyList()

        val objectPattern = Regex("""\{[^}]+\}""")
        return objectPattern.findAll(json).map { match ->
            val obj = match.value
            RelationshipView(
                fromId = extractJsonField(obj, "fromId"),
                toId = extractJsonField(obj, "toId"),
                type = extractJsonField(obj, "type")
            )
        }.toList()
    }

    private fun extractJsonField(jsonObject: String, field: String): String {
        val pattern = Regex(""""$field":"((?:\\\\|\\\"|[^\"])*)"""")
        val raw = pattern.find(jsonObject)?.groupValues?.get(1)
            ?: error("Missing field '$field' in $jsonObject")
        return raw
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun findDesignElement(json: String, tag: String, name: String): String {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        json.forEachIndexed { index, ch ->
            if (escape) {
                escape = false
                return@forEachIndexed
            }
            if (ch == '\\' && inString) {
                escape = true
                return@forEachIndexed
            }
            if (ch == '"') {
                inString = !inString
                return@forEachIndexed
            }
            if (inString) {
                return@forEachIndexed
            }
            when (ch) {
                '{' -> {
                    if (depth == 0) {
                        start = index
                    }
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += json.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects.firstOrNull {
            it.contains(""""tag":"$tag"""") && it.contains(""""name":"$name"""")
        } ?: error("Missing design element tag=$tag name=$name")
    }

    private fun assertMethodEdgeShape(
        rels: List<RelationshipView>,
        handlerId: String,
        aggregateId: String,
        methodId: String,
        eventId: String,
        wrongMethodIds: Set<String>,
    ) {
        val expected = setOf(
            RelationshipView(handlerId, methodId, "CommandHandlerToEntityMethod"),
            RelationshipView(aggregateId, methodId, "AggregateToEntityMethod"),
            RelationshipView(methodId, eventId, "EntityMethodToDomainEvent")
        )

        val relevant = rels.filter {
            it.toId == methodId ||
                it.fromId == methodId ||
                it.fromId in wrongMethodIds ||
                it.toId in wrongMethodIds
        }.toSet()

        assertEquals(expected, relevant, "Unexpected relevant relationships: $relevant")
        wrongMethodIds.forEach { wrongId ->
            assertEquals(
                0,
                rels.count { it.fromId == wrongId || it.toId == wrongId },
                "Wrong method id leaked into graph: $wrongId"
            )
        }
        assertEquals(
            1,
            rels.count { it.fromId == handlerId && it.toId == methodId && it.type == "CommandHandlerToEntityMethod" }
        )
        assertEquals(
            1,
            rels.count { it.fromId == aggregateId && it.toId == methodId && it.type == "AggregateToEntityMethod" }
        )
        assertEquals(
            1,
            rels.count { it.fromId == methodId && it.toId == eventId && it.type == "EntityMethodToDomainEvent" }
        )
    }

    private fun assertCrossModuleMethodEdgeShape(
        rels: List<RelationshipView>,
        handlerId: String,
        aggregateId: String,
        methodId: String,
        wrongMethodIds: Set<String>,
    ) {
        val expected = setOf(
            RelationshipView(handlerId, methodId, "CommandHandlerToEntityMethod"),
            RelationshipView(aggregateId, methodId, "AggregateToEntityMethod"),
        )

        val relevant = rels.filter {
            it.toId == methodId ||
                it.fromId == methodId ||
                it.fromId in wrongMethodIds ||
                it.toId in wrongMethodIds
        }.toSet()

        assertEquals(expected, relevant, "Unexpected relevant relationships: $relevant")
        wrongMethodIds.forEach { wrongId ->
            assertEquals(
                0,
                rels.count { it.fromId == wrongId || it.toId == wrongId },
                "Wrong method id leaked into graph: $wrongId"
            )
        }
        assertEquals(
            1,
            rels.count { it.fromId == handlerId && it.toId == methodId && it.type == "CommandHandlerToEntityMethod" }
        )
        assertEquals(
            1,
            rels.count { it.fromId == aggregateId && it.toId == methodId && it.type == "AggregateToEntityMethod" }
        )
        assertEquals(
            0,
            rels.count { it.fromId == methodId && it.type == "EntityMethodToDomainEvent" },
            "Cross-module handler compile should not infer external method body domain events"
        )
    }

    private fun categorySources(
        categoryBody: String = DEFAULT_CATEGORY_BODY,
        useTopLevelBehavior: Boolean,
        behaviorBody: String,
    ): List<SourceFile> {
        val behaviorImport = if (useTopLevelBehavior) {
            "import demo.domain.aggregates.category.changeSort"
        } else {
            ""
        }
        val sources = mutableListOf(
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface Command<RESULT : Any>
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CommandHandler.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface CommandHandler<COMMAND : Command<RESULT>, RESULT : Any> {
                        fun handle(command: COMMAND): RESULT
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "AggregateElementMetadata.kt",
                """
                    package com.only4.cap4k.analysis.metadata

                    annotation class AggregateElementMetadata(
                        val aggregate: String = "",
                        val type: String = "",
                        val name: String = "",
                        val packageName: String = "",
                        val root: Boolean = false
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "JpaAnnotations.kt",
                """
                    package jakarta.persistence

                    annotation class Entity
                    annotation class Table(val name: String = "")
                    annotation class Id
                    annotation class EmbeddedId
                    annotation class Column(
                        val name: String = "",
                        val insertable: Boolean = true,
                        val updatable: Boolean = true,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "DomainEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.event.annotation

                    annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Category.kt",
                """
                    package demo.domain.aggregates.category

                    import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
                    import demo.domain.aggregates.category.events.CategorySortChanged

                    $categoryBody
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategorySortChanged.kt",
                """
                    package demo.domain.aggregates.category.events

                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    @DomainEvent
                    data class CategorySortChanged(val sort: Int)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UpdateCategorySortCmd.kt",
                """
                    package demo.application.commands.category

                    import com.only4.cap4k.ddd.core.application.command.Command
                    import com.only4.cap4k.ddd.core.application.command.CommandHandler
                    import demo.domain.aggregates.category.Category
                    $behaviorImport

                    class UpdateCategorySortCmd(val sort: Int) : Command<UpdateCategorySortCmd.Response> {
                        class Response

                        class Handler : CommandHandler<UpdateCategorySortCmd, Response> {
                            override fun handle(command: UpdateCategorySortCmd): Response {
                                val category = Category()
                                category.changeSort(command.sort)
                                return Response()
                            }
                        }
                    }
                """.trimIndent()
            )
        )

        if (behaviorBody.isNotBlank()) {
            sources += SourceFile.kotlin(
                "CategoryBehavior.kt",
                """
                    package demo.domain.aggregates.category

                    import demo.domain.aggregates.category.events.CategorySortChanged

                    $behaviorBody
                """.trimIndent()
            )
        }

        return sources
    }

    private fun categoryDomainLibrarySources(
        behaviorBody: String,
        categoryBody: String = DEFAULT_CATEGORY_BODY,
    ): List<SourceFile> {
        return listOf(
            SourceFile.kotlin(
                "AggregateElementMetadata.kt",
                """
                    package com.only4.cap4k.analysis.metadata

                    annotation class AggregateElementMetadata(
                        val aggregate: String = "",
                        val type: String = "",
                        val name: String = "",
                        val packageName: String = "",
                        val root: Boolean = false
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "JpaAnnotations.kt",
                """
                    package jakarta.persistence

                    annotation class Entity
                    annotation class Table(val name: String = "")
                    annotation class Id
                    annotation class EmbeddedId
                    annotation class Column(
                        val name: String = "",
                        val insertable: Boolean = true,
                        val updatable: Boolean = true,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "DomainEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.event.annotation

                    annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Category.kt",
                """
                    package demo.domain.aggregates.category

                    import com.only4.cap4k.analysis.metadata.AggregateElementMetadata

                    $categoryBody
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategorySortChanged.kt",
                """
                    package demo.domain.aggregates.category.events

                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    @DomainEvent
                    data class CategorySortChanged(val sort: Int)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategoryBehavior.kt",
                """
                    package demo.domain.aggregates.category

                    import demo.domain.aggregates.category.events.CategorySortChanged

                    $behaviorBody
                """.trimIndent()
            ),
        )
    }

    private fun categoryAppSources(useTopLevelBehavior: Boolean): List<SourceFile> {
        val behaviorImport = if (useTopLevelBehavior) {
            "import demo.domain.aggregates.category.changeSort"
        } else {
            ""
        }
        return listOf(
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface Command<RESULT : Any>
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CommandHandler.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface CommandHandler<COMMAND : Command<RESULT>, RESULT : Any> {
                        fun handle(command: COMMAND): RESULT
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UpdateCategorySortCmd.kt",
                """
                    package demo.application.commands.category

                    import com.only4.cap4k.ddd.core.application.command.Command
                    import com.only4.cap4k.ddd.core.application.command.CommandHandler
                    import demo.domain.aggregates.category.Category
                    $behaviorImport

                    class UpdateCategorySortCmd(val sort: Int) : Command<UpdateCategorySortCmd.Response> {
                        class Response

                        class Handler : CommandHandler<UpdateCategorySortCmd, Response> {
                            override fun handle(command: UpdateCategorySortCmd): Response {
                                val category = Category()
                                category.changeSort(command.sort)
                                return Response()
                            }
                        }
                    }
                """.trimIndent()
            ),
        )
    }

    private fun stableDefaultSources(
        channelsType: String,
        channelsDefaultExpression: String,
        referenceTitleDefaultExpression: String = "SHARED_FIELD_DEFAULT_TITLE",
        preferredChannelDefaultExpression: String = "CaptchaChannel.INLINE",
        policyDefaultExpression: String = "CaptchaPolicy",
        privateReferenceTitleDefaultExpression: String = "TOP_LEVEL_GETTER_DEFAULT_TITLE",
        extraRequestFields: String = "",
    ): List<SourceFile> {
        return listOf(
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface Command<RESULT : Any>
                """.trimIndent(),
            ),
            SourceFile.java(
                "CaptchaStableDefaults.java",
                """
                    package demo.application.shared.defaults;

                    public final class CaptchaStableDefaults {
                        public static final String DEFAULT_TITLE = new String("const-inline");
                        public static final String DYNAMIC_TITLE = new StringBuilder().append("dynamic-java-inline").toString();

                        private CaptchaStableDefaults() {
                        }
                    }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "SharedDefaults.kt",
                """
                    @file:JvmName("SharedDefaults")

                    package demo.application.shared.defaults

                    enum class SharedCaptchaChannel {
                        IMAGE,
                        SMS,
                    }

                    object SharedCaptchaPolicy

                    @JvmField
                    val TOP_LEVEL_DEFAULT_TITLE: String = "top-level-inline"

                    @JvmField
                    val SHARED_FIELD_DEFAULT_TITLE: String = "shared-field-inline"

                    @JvmField
                    val DYNAMIC_TOP_LEVEL_DEFAULT_TITLE: String = buildString {
                        append("dynamic-top-level-inline")
                    }

                    val TOP_LEVEL_GETTER_DEFAULT_TITLE: String = "top-level-getter-inline"

                    object SharedGetterDefaults {
                        val OBJECT_DEFAULT_TITLE: String = "object-getter-inline"
                    }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "DesignBlockMetadata.kt",
                """
                    package com.only4.cap4k.analysis.metadata

                    annotation class DesignBlockMetadata(
                        val tag: String,
                        val name: String,
                        val packageName: String,
                        val description: String = "",
                        val aggregates: Array<String> = [],
                        val eventName: String = "",
                        val family: String = "",
                        val variant: String = "",
                    )
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "IssueCaptchaCmd.kt",
                """
                    package demo.application.commands.auth

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                    import com.only4.cap4k.ddd.core.application.command.Command
                    import demo.application.shared.defaults.CaptchaStableDefaults
                    import demo.application.shared.defaults.SharedCaptchaChannel
                    import demo.application.shared.defaults.SharedCaptchaPolicy
                    import demo.application.shared.defaults.SharedGetterDefaults
                    import demo.application.shared.defaults.DYNAMIC_TOP_LEVEL_DEFAULT_TITLE
                    import demo.application.shared.defaults.SHARED_FIELD_DEFAULT_TITLE
                    import demo.application.shared.defaults.TOP_LEVEL_DEFAULT_TITLE
                    import demo.application.shared.defaults.TOP_LEVEL_GETTER_DEFAULT_TITLE

                    enum class CaptchaChannel {
                        INLINE,
                        SMS,
                    }

                    private enum class PrivateCaptchaChannel {
                        INLINE,
                        SMS,
                    }

                    object CaptchaDefaults {
                        val dynamicTitle: String
                            get() = CaptchaStableDefaults.DEFAULT_TITLE.lowercase()
                    }

                    class StableInstanceDefaults {
                        val defaultTitle: String = "instance-inline"
                    }

                    private object PrivateCaptchaDefaults {
                        val PRIVATE_OBJECT_DEFAULT_TITLE: String = "private-object-inline"
                    }

                    private object PrivateCaptchaPolicy

                    object CaptchaPolicy

                    object IssueCaptchaCmd {
                        @DesignBlockMetadata(
                            tag = "command",
                            packageName = "auth",
                            name = "IssueCaptcha",
                            description = "issue captcha",
                            family = "command",
                        )
                        data class Request(
                            val note: String? = null,
                            val title: String = "inline",
                            val attempt: Int = 1,
                            val enabled: Boolean = true,
                            val tags: List<String> = emptyList(),
                            val channels: $channelsType = $channelsDefaultExpression,
                            val metadata: Map<String, String> = emptyMap(),
                            val preferredChannel: CaptchaChannel = $preferredChannelDefaultExpression,
                            val policy: CaptchaPolicy = $policyDefaultExpression,
                            val referenceTitle: String = $referenceTitleDefaultExpression,
                            val externalPreferredChannel: SharedCaptchaChannel = SharedCaptchaChannel.IMAGE,
                            val externalPolicy: SharedCaptchaPolicy = SharedCaptchaPolicy,
                            val topLevelReferenceTitle: String = TOP_LEVEL_DEFAULT_TITLE,
                            val topLevelGetterReferenceTitle: String = TOP_LEVEL_GETTER_DEFAULT_TITLE,
                            val objectGetterReferenceTitle: String = SharedGetterDefaults.OBJECT_DEFAULT_TITLE,
                            val privateReferenceTitle: String = $privateReferenceTitleDefaultExpression,
                            $extraRequestFields
                        ) : Command<IssueCaptchaCmd.Response> {
                            data class Response(val issued: Boolean)
                        }

                        data class Response(val issued: Boolean)
                    }
                """.trimIndent(),
            ),
        )
    }

    private data class RelationshipView(
        val fromId: String,
        val toId: String,
        val type: String,
    )

    private fun invokeResolveDefaultValue(
        collector: DesignElementCollector,
        param: IrValueParameter,
        context: String,
    ): String? {
        val renderStyleClass = DesignElementCollector::class.java.declaredClasses
            .single { it.simpleName == "DefaultValueRenderStyle" }
        val kotlinReady = renderStyleClass.enumConstants.single {
            (it as Enum<*>).name == "KOTLIN_READY"
        }
        val method = DesignElementCollector::class.java.getDeclaredMethod(
            "resolveDefaultValue",
            IrValueParameter::class.java,
            String::class.java,
            renderStyleClass,
        )
        method.isAccessible = true
        return try {
            method.invoke(collector, param, context, kotlinReady) as String?
        } catch (ex: java.lang.reflect.InvocationTargetException) {
            throw (ex.targetException ?: ex)
        }
    }

    private fun invokeIsStableConstantField(
        collector: DesignElementCollector,
        field: IrField,
    ): Boolean {
        val method = DesignElementCollector::class.java.getDeclaredMethod(
            "isStableConstantField",
            IrField::class.java,
        )
        method.isAccessible = true
        return method.invoke(collector, field) as Boolean
    }

    private fun irValueParameterWithDefault(
        name: String,
        expression: IrExpression,
    ): IrValueParameter {
        return irFactory().createValueParameter(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            IrDeclarationOrigin.DEFINED,
            Name.identifier(name),
            dynamicIrType(),
            false,
            IrValueParameterSymbolImpl(),
            null,
            false,
            false,
            false,
        ).apply {
            defaultValue = irFactory().createExpressionBody(expression)
        }
    }

    private fun irFieldWithInitializer(expression: IrExpression): IrField {
        return irFactory().createField(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            IrDeclarationOrigin.DEFINED,
            Name.identifier("BLOCK_DEFAULT_TITLE"),
            DescriptorVisibilities.PUBLIC,
            IrFieldSymbolImpl(),
            dynamicIrType(),
            false,
            true,
            false,
        ).apply {
            isFinal = true
            isStatic = false
            initializer = irFactory().createExpressionBody(expression)
        }
    }

    private fun irCompositeExpression(vararg statements: IrExpression): IrExpression {
        return IrCompositeImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            dynamicIrType(),
            null,
            statements.toList(),
        )
    }

    private fun irBlockExpression(vararg statements: IrExpression): IrExpression {
        val constructor = IrBlockImpl::class.java.getDeclaredConstructor(
            Class.forName("org.jetbrains.kotlin.ir.util.IrElementConstructorIndicator"),
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            IrType::class.java,
            Class.forName("org.jetbrains.kotlin.ir.expressions.IrStatementOrigin"),
        )
        constructor.isAccessible = true
        return (constructor.newInstance(
            null,
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            dynamicIrType(),
            null,
        ) as IrBlockImpl).apply {
            this.statements += statements
        }
    }

    private fun irStringConst(value: String): IrExpression =
        IrConstImpl.Companion.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, dynamicIrType(), value)

    private fun irIntConst(value: Int): IrExpression =
        IrConstImpl.Companion.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, dynamicIrType(), value)

    private fun dynamicIrType(): IrType = IrDynamicTypeImpl(emptyList(), Variance.INVARIANT)

    private fun irFactory(): IrFactoryImpl = IrFactoryImpl

    companion object {
        private const val UNDEFINED_OFFSET = -1
        private const val DEFAULT_CATEGORY_BODY = """
            @AggregateElementMetadata(
                aggregate = "Category",
                type = "entity",
                name = "Category",
                packageName = "demo.domain.aggregates.category",
                root = true,
            )
            class Category
        """
        private const val GENERATED_STYLE_CATEGORY_BODY = """
            import jakarta.persistence.Entity
            import jakarta.persistence.Table

            @AggregateElementMetadata(
                aggregate = "Category",
                type = "entity",
                name = "Category",
                packageName = "demo.domain.aggregates.category",
                root = true,
            )
            @Entity
            @Table(name = "category")
            class Category()
        """
        private const val GENERATED_STYLE_CATEGORY_BODY_WITHOUT_AGGREGATE_ANNOTATION = """
            import jakarta.persistence.Entity
            import jakarta.persistence.EmbeddedId
            import jakarta.persistence.Table

            @Entity
            @Table(name = "category")
            class Category {
                @EmbeddedId
                var id: String = ""
            }
        """
    }
}
