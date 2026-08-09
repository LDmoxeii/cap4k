package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementExtractionTest {
    @Test
    fun `analyzer consumes the published analysis metadata annotation abi`() {
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
                    "RealMetadataCommand.kt",
                    """
                        package demo.application.commands

                        import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                        import com.only4.cap4k.ddd.core.application.command.Command

                        @DesignBlockMetadata(
                            tag = "command",
                            name = "RealMetadata",
                            packageName = "real",
                            family = "command",
                        )
                        object RealMetadataCmd {
                            data class Request(val id: Long) : Command<Response>
                            data class Response(val accepted: Boolean)
                        }
                    """.trimIndent(),
                ),
            )
        )

        val json = outputDir.resolve("design-elements.json").toFile().readText()
        assertTrue(json.contains("\"name\":\"RealMetadata\""), json)
        assertTrue(json.contains("\"family\":\"command\""), json)
        assertTrue(json.contains("\"name\":\"id\",\"type\":\"Long\""), json)
    }

    @Test
    fun `recovers and merges design blocks from DesignBlockMetadata annotations`() {
        val sources = listOf(
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
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindOrder.kt",
                """
                    package demo.application.queries.order

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrder",
                        description = "Find order",
                        aggregates = ["Order"],
                        family = "query",
                        variant = "detail",
                    )
                    data class FindOrder(
                        val orderId: Long,
                        val keyword: String? = null,
                    ) {
                        data class Response(val orderNo: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindOrderQueryHandler.kt",
                """
                    package demo.adapter.queries.order

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrder",
                        description = "Find order",
                        aggregates = ["Order"],
                        family = "query-handler",
                    )
                    class FindOrderQueryHandler
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val objects = extractTopLevelObjects(json)
        val findOrderBlocks = objects.filter { it.contains("\"tag\":\"query\"") && it.contains("\"name\":\"FindOrder\"") }

        assertEquals(1, findOrderBlocks.size)
        val findOrder = findOrderBlocks.single()
        assertTrue(findOrder.contains("\"package\":\"order.read\""))
        assertTrue(findOrder.contains("\"description\":\"Find order\""))
        assertTrue(findOrder.contains("\"aggregates\":[\"Order\"]"))
        assertTrue(findOrder.contains("\"artifacts\":[{\"family\":\"query\",\"variant\":\"detail\"},{\"family\":\"query-handler\"}]"))
        assertFalse(findOrder.contains("\"eventName\""))
        assertFalse(findOrder.contains("\"family\":\"query-handler\",\"variant\""))
        assertTrue(findOrder.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\"}"))
        assertTrue(findOrder.contains("\"name\":\"keyword\",\"type\":\"String?\",\"defaultValue\":\"null\""))
        assertTrue(findOrder.contains("\"resultFields\":[{\"name\":\"orderNo\",\"type\":\"String\"}]"))
        assertFalse(json.contains("\"desc\""))
        assertFalse(json.contains("\"traits\""))
        assertFalse(json.contains("\"role\""))
        assertFalse(json.contains("\"entity\""))
        assertFalse(json.contains("\"requestFields\""))
        assertFalse(json.contains("\"responseFields\""))
    }

    @Test
    fun `recovers generated outer DesignBlockMetadata command request and response`() {
        val sources = listOf(
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
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "SubmitOrderCmd.kt",
                """
                    package demo.application.commands.order

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "command",
                        packageName = "order.submit",
                        name = "SubmitOrder",
                        description = "Submit order",
                        aggregates = ["Order"],
                        family = "command",
                    )
                    object SubmitOrderCmd {
                        data class Request(
                            val orderId: Long,
                            val note: String? = null,
                        )

                        data class Response(val accepted: Boolean)
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val submitOrder = findObject(extractTopLevelObjects(json), "command", "SubmitOrder")

        assertTrue(submitOrder.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\"}"))
        assertTrue(submitOrder.contains("\"name\":\"note\",\"type\":\"String?\",\"defaultValue\":\"null\""))
        assertTrue(submitOrder.contains("\"resultFields\":[{\"name\":\"accepted\",\"type\":\"Boolean\"}]"))
    }

    @Test
    fun `rejects conflicting DesignBlockMetadata shared metadata`() {
        val sources = listOf(
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
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindOrder.kt",
                """
                    package demo.application.queries.order

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrder",
                        description = "Find order",
                        family = "query",
                    )
                    class FindOrder
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindOrderQueryHandler.kt",
                """
                    package demo.adapter.queries.order

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrder",
                        description = "Find order differently",
                        family = "query-handler",
                    )
                    class FindOrderQueryHandler
                """.trimIndent()
            )
        )

        val messages = compileWithCap4kPluginExpectingFailure(sources)

        assertTrue(
            messages.contains("conflicting DesignBlockMetadata for query order.read FindOrder: description"),
        )
    }

    @Test
    fun `rejects DesignBlockMetadata annotations with blank required identity`() {
        val annotationSource = SourceFile.kotlin(
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
            """.trimIndent()
        )
        val blankTagMessages = compileWithCap4kPluginExpectingFailure(
            listOf(
                annotationSource,
                SourceFile.kotlin(
                    "BlankTagBlock.kt",
                    """
                        package demo.application.queries.order

                        import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                        @DesignBlockMetadata(
                            tag = " ",
                            packageName = "order.read",
                            name = "FindOrder",
                            family = "query",
                        )
                        class BlankTagBlock
                    """.trimIndent()
                ),
            )
        )
        val blankFamilyMessages = compileWithCap4kPluginExpectingFailure(
            listOf(
                annotationSource,
                SourceFile.kotlin(
                    "BlankFamilyBlock.kt",
                    """
                        package demo.application.queries.order

                        import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                        @DesignBlockMetadata(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            family = " ",
                        )
                        class BlankFamilyBlock
                    """.trimIndent()
                ),
            )
        )

        assertTrue(
            blankTagMessages.contains("DesignBlockMetadata annotation on demo.application.queries.order.BlankTagBlock must declare non-blank tag"),
        )
        assertTrue(
            blankFamilyMessages.contains("DesignBlockMetadata annotation on demo.application.queries.order.BlankFamilyBlock must declare non-blank family"),
        )
    }

    @Test
    fun `query handler dependencies do not become recovered fields`() {
        val sources = listOf(
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
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindCustomer.kt",
                """
                    package demo.application.queries.customer

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "customer.read",
                        name = "FindCustomer",
                        family = "query",
                    )
                    data class FindCustomer(
                        val customerId: Long,
                    ) {
                        data class Response(val displayName: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindCustomerQueryHandler.kt",
                """
                    package demo.adapter.queries.customer

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    interface CustomerReadRepository
                    interface ClockProvider

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "customer.read",
                        name = "FindCustomer",
                        family = "query-handler",
                    )
                    class FindCustomerQueryHandler(
                        private val repository: CustomerReadRepository,
                        private val clockProvider: ClockProvider,
                    )
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val findCustomer = findObject(extractTopLevelObjects(json), "query", "FindCustomer")

        assertTrue(findCustomer.contains("\"artifacts\":[{\"family\":\"query\"},{\"family\":\"query-handler\"}]"))
        assertTrue(findCustomer.contains("\"fields\":[{\"name\":\"customerId\",\"type\":\"Long\"}]"))
        assertTrue(findCustomer.contains("\"resultFields\":[{\"name\":\"displayName\",\"type\":\"String\"}]"))
        assertFalse(findCustomer.contains("repository"))
        assertFalse(findCustomer.contains("clockProvider"))
    }

    @Test
    fun `handwritten business bodies do not change recovered design block semantics`() {
        val metadata = SourceFile.kotlin(
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
        )
        val primary = SourceFile.kotlin(
            "FindCustomer.kt",
            """
                package demo.application.queries.customer

                import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                @DesignBlockMetadata(
                    tag = "query",
                    packageName = "customer.read",
                    name = "FindCustomer",
                    description = "Find one customer",
                    aggregates = ["Customer"],
                    family = "query",
                )
                object FindCustomer {
                    data class Request(val customerId: Long)
                    data class Response(val displayName: String)
                }
            """.trimIndent(),
        )

        fun recoveredBlock(handlerSource: SourceFile): String {
            val outputDir = compileWithCap4kPlugin(listOf(metadata, primary, handlerSource))
            val json = outputDir.resolve("design-elements.json").toFile().readText()
            return findObject(extractTopLevelObjects(json), "query", "FindCustomer")
        }

        val repositoryImplementation = recoveredBlock(
            SourceFile.kotlin(
                "FindCustomerQueryHandler.kt",
                """
                    package demo.adapter.queries.customer

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                    import demo.application.queries.customer.FindCustomer

                    interface CustomerReadRepository {
                        fun findDisplayName(customerId: Long): String
                    }

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "customer.read",
                        name = "FindCustomer",
                        description = "Find one customer",
                        aggregates = ["Customer"],
                        family = "query-handler",
                    )
                    class FindCustomerQueryHandler(
                        private val repository: CustomerReadRepository,
                    ) {
                        fun execute(request: FindCustomer.Request): FindCustomer.Response {
                            val displayName = repository.findDisplayName(request.customerId)
                            return FindCustomer.Response(displayName)
                        }
                    }
                """.trimIndent(),
            ),
        )
        val alternateBusinessImplementation = recoveredBlock(
            SourceFile.kotlin(
                "FindCustomerQueryHandler.kt",
                """
                    package demo.adapter.queries.customer

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                    import demo.application.queries.customer.FindCustomer

                    interface CustomerSnapshotRepository {
                        fun lookupName(customerId: Long): String
                    }
                    interface QueryAudit {
                        fun record(customerId: Long, displayName: String)
                    }

                    @DesignBlockMetadata(
                        tag = "query",
                        packageName = "customer.read",
                        name = "FindCustomer",
                        description = "Find one customer",
                        aggregates = ["Customer"],
                        family = "query-handler",
                    )
                    class FindCustomerQueryHandler(
                        private val snapshots: CustomerSnapshotRepository,
                        private val audit: QueryAudit,
                    ) {
                        fun execute(request: FindCustomer.Request): FindCustomer.Response {
                            val displayName = snapshots.lookupName(request.customerId).uppercase()
                            audit.record(request.customerId, displayName)
                            return FindCustomer.Response(displayName)
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertEquals(repositoryImplementation, alternateBusinessImplementation)
        assertTrue(
            repositoryImplementation.contains(
                "\"artifacts\":[{\"family\":\"query\"},{\"family\":\"query-handler\"}]",
            ),
        )
        assertFalse(repositoryImplementation.contains("repository"))
        assertFalse(repositoryImplementation.contains("snapshots"))
        assertFalse(repositoryImplementation.contains("audit"))
    }

    @Test
    fun `domain event recovery preserves ordinary entity field and runtime event contract`() {
        val sources = listOf(
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
                "OrderCreated.kt",
                """
                    package demo.domain.aggregates.order.events

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    class Order

                    @DesignBlockMetadata(
                        tag = "domain_event",
                        packageName = "order.events",
                        name = "OrderCreated",
                        description = "order created",
                        aggregates = ["Order"],
                        eventName = "order.created",
                        family = "domain-event",
                    )
                    @DomainEvent(value = "order.created", persist = true)
                    data class OrderCreated(
                        val entity: Order,
                        val orderId: Long,
                        val reason: String? = null,
                    )
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val orderCreated = findObject(extractTopLevelObjects(json), "domain_event", "OrderCreated")

        assertTrue(orderCreated.contains("\"artifacts\":[{\"family\":\"domain-event\"}]"))
        assertTrue(orderCreated.contains("\"eventName\":\"order.created\""))
        assertTrue(orderCreated.contains("\"persist\":true"))
        assertTrue(
            orderCreated.contains(
                "\"fields\":[{\"name\":\"entity\",\"type\":\"demo.domain.aggregates.order.events.Order\"}",
            ),
        )
        assertTrue(orderCreated.contains("{\"name\":\"orderId\",\"type\":\"Long\"}"))
        assertTrue(orderCreated.contains("\"name\":\"reason\",\"type\":\"String?\",\"defaultValue\":\"null\""))
        assertFalse(json.contains("\"requestFields\""))
        assertFalse(json.contains("\"responseFields\""))
    }

    @Test
    fun `integration subscriber dependencies do not conflict with event body fields`() {
        val sources = listOf(
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
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "IntegrationEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.application.event.annotation
                    annotation class IntegrationEvent(val value: String = "")
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "PaymentReceivedIntegrationEvent.kt",
                """
                    package demo.application.subscribers.integration.inbound.payment

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                    import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent

                    @IntegrationEvent(value = "demo.payment.received")
                    @DesignBlockMetadata(
                        tag = "integration_event",
                        packageName = "payment.integration",
                        name = "PaymentReceived",
                        eventName = "demo.payment.received",
                        family = "integration-event",
                        variant = "inbound",
                    )
                    data class PaymentReceivedIntegrationEvent(
                        val paymentId: String,
                        val amount: Long,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "PaymentReceivedSubscriber.kt",
                """
                    package demo.application.subscribers.integration.inbound.payment

                    import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

                    interface PaymentCommandPort
                    interface AuditTrail

                    @DesignBlockMetadata(
                        tag = "integration_event",
                        packageName = "payment.integration",
                        name = "PaymentReceived",
                        family = "integration-subscriber",
                    )
                    class PaymentReceivedSubscriber(
                        private val commandPort: PaymentCommandPort,
                        private val auditTrail: AuditTrail,
                    )
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val paymentReceived = findObject(extractTopLevelObjects(json), "integration_event", "PaymentReceived")

        assertTrue(
            paymentReceived.contains(
                "\"artifacts\":[{\"family\":\"integration-event\",\"variant\":\"inbound\"},{\"family\":\"integration-subscriber\"}]",
            ),
        )
        assertTrue(paymentReceived.contains("\"eventName\":\"demo.payment.received\""))
        assertTrue(paymentReceived.contains("\"fields\":[{\"name\":\"paymentId\",\"type\":\"String\"}"))
        assertTrue(paymentReceived.contains("\"name\":\"amount\",\"type\":\"Long\"}"))
        assertTrue(paymentReceived.contains("\"resultFields\":[]"))
        assertFalse(paymentReceived.contains("commandPort"))
        assertFalse(paymentReceived.contains("auditTrail"))
    }

    @Test
    fun `emits design-elements json from request and payload`() {
        val sources = listOf(
            SourceFile.kotlin(
                "Command.kt",
                "package com.only4.cap4k.ddd.core.application.command; interface Command<T>"
            ),
            SourceFile.kotlin(
                "Query.kt",
                "package com.only4.cap4k.ddd.core.application.query; interface Query<T>"
            ),
            SourceFile.kotlin(
                "CapabilityCall.kt",
                "package com.only4.cap4k.ddd.core.application.capability; interface CapabilityCall<T>"
            ),
            SourceFile.kotlin(
                "PageRequest.kt",
                """
                    package com.only4.cap4k.ddd.core.application.query
                    interface PageRequest {
                        val pageNum: Int
                        val pageSize: Int
                    }
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
                "IntegrationEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.application.event.annotation
                    annotation class IntegrationEvent(val value: String = "")
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "AggregateElementMetadata.kt",
                """
                    package com.only4.cap4k.analysis.metadata
                    annotation class AggregateElementMetadata(
                        val aggregate: String = "",
                        val type: String = "",
                        val root: Boolean = false
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "IssueTokenCmd.kt",
                """
                    package demo.application.commands.authorize
                    class IssueTokenCmd : com.only4.cap4k.ddd.core.application.command.Command<IssueTokenCmd.Response> {
                        data class Request(val userId: Long, val note: String = "x")
                        data class Response(val token: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "SubmitOrderCmd.kt",
                """
                    package demo.application.commands.orders
                    object SubmitOrderCmd {
                        data class Request(val cmdValue: String) : com.only4.cap4k.ddd.core.application.command.Command<Response>
                        data class Response(val cmdResult: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "AutoLoginQry.kt",
                """
                    package demo.application.queries.session
                    object AutoLoginQry {
                        class Request : com.only4.cap4k.ddd.core.application.query.Query<Response>
                        data class Response(val sessionToken: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CaptchaGen.kt",
                """
                    package demo.application.capabilities.auth
                    object CaptchaGen {
                        data class Request(val capabilityAccount: String) : com.only4.cap4k.ddd.core.application.capability.CapabilityCall<Response>
                        data class Response(val captchaId: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "TopCmd.kt",
                """
                    package demo.application.commands
                    object TopCmd {
                        data class Request(val id: Long) : com.only4.cap4k.ddd.core.application.command.Command<Response>
                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "TopQry.kt",
                """
                    package demo.application.queries
                    object TopQry {
                        class Request : com.only4.cap4k.ddd.core.application.query.Query<Response>
                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FindOrderPageQry.kt",
                """
                    package demo.application.queries.orders
                    object FindOrderPageQry {
                        data class Request(
                            override val pageNum: Int = 1,
                            override val pageSize: Int = 10,
                            val keyword: String? = null,
                        ) : com.only4.cap4k.ddd.core.application.query.PageRequest,
                            com.only4.cap4k.ddd.core.application.query.Query<Response>
                        data class Response(val page: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Top.kt",
                """
                    package demo.application.capabilities
                    object Top {
                        data class Request(val token: String) : com.only4.cap4k.ddd.core.application.capability.CapabilityCall<Response>
                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FireAndForgetCmd.kt",
                """
                    package demo.application.commands.notice
                    object FireAndForgetCmd {
                        data class Request(val message: String) : com.only4.cap4k.ddd.core.application.command.Command<Unit>
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UserCreated.kt",
                """
                    package demo.domain.aggregates.user.events
                    @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(persist = true)
                    data class UserCreated(val userId: Long)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "MediaProcessingCallbackIntegrationEvent.kt",
                """
                    package com.acme.application.subscribers.integration.inbound.media.processing
                    import java.time.LocalDateTime

                    @com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent(
                        value = "cap4k.reference.contentstudio.media-processing.succeeded",
                    )
                    data class MediaProcessingCallbackIntegrationEvent(
                        val externalTaskId: String,
                        val completedAt: LocalDateTime,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "IgnoredRuntimeIntegrationEvent.kt",
                """
                    package com.acme.application.events

                    @com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent(
                        value = "cap4k.reference.ignored"
                    )
                    data class IgnoredRuntimeIntegrationEvent(val externalTaskId: String)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "ContentPublishedIntegrationEvent.kt",
                """
                    package com.acme.application.subscribers.integration.outbound.content

                    @com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent(
                        value = "cap4k.reference.content.published",
                    )
                    data class ContentPublishedIntegrationEvent(val contentId: Long)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "BatchSaveAccountList.kt",
                """
                    package demo.adapter.portal.api.payload.account
                    object BatchSaveAccountList {
                        data class Request(val globalId: String, val account: AccountInfo)
                        data class Response(val result: Boolean)
                        data class AccountInfo(val accountNumber: String)
                        interface Converter {
                            companion object
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "GetOrderPage.kt",
                """
                    package demo.adapter.portal.api.payload.order
                    object GetOrderPage {
                        data class Request(
                            override val pageNum: Int = 1,
                            override val pageSize: Int = 10,
                            val keyword: String? = null,
                        ) : com.only4.cap4k.ddd.core.application.query.PageRequest
                        data class Response(val page: String)
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        assertEquals("[]", json)
    }

    @Test
    fun `emits supported ordinary validators and skips unique or concrete request validators`() {
        val sources = listOf(
            SourceFile.kotlin(
                "ValidationStubs.kt",
                """
                    package jakarta.validation
                    import kotlin.reflect.KClass
                    annotation class Constraint(val validatedBy: Array<KClass<*>>)
                    interface ConstraintValidator<A : Annotation, T> {
                        fun isValid(value: T?, context: ConstraintValidatorContext): Boolean
                    }
                    interface ConstraintValidatorContext
                    interface Payload
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategoryMustExist.kt",
                """
                    package demo.application.validators.category
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [CategoryMustExist.Validator::class])
                    annotation class CategoryMustExist(
                        val message: String = "category missing",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                    ) {
                        class Validator : ConstraintValidator<CategoryMustExist, Long> {
                            override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "DanmukuDeletePermission.kt",
                """
                    package demo.application.validators.danmuku
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    @Target(AnnotationTarget.CLASS)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [DanmukuDeletePermission.Validator::class])
                    annotation class DanmukuDeletePermission(
                        val message: String = "no delete permission",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                        val danmukuIdField: String = "danmukuId",
                        val operatorIdField: String = "operatorId",
                    ) {
                        class Validator : ConstraintValidator<DanmukuDeletePermission, Any> {
                            override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UniqueUserMessageMessageKey.kt",
                """
                    package demo.application.validators.user_message.unique
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    @Target(AnnotationTarget.CLASS)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [UniqueUserMessageMessageKey.Validator::class])
                    annotation class UniqueUserMessageMessageKey(
                        val message: String = "duplicate",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                    ) {
                        class Validator : ConstraintValidator<UniqueUserMessageMessageKey, Any> {
                            override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "VideoDeletePermission.kt",
                """
                    package demo.application.validators.video
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    object DeleteVideoPostCmd {
                        data class Request(val videoId: Long)
                    }

                    @Target(AnnotationTarget.CLASS)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [VideoDeletePermission.Validator::class])
                    annotation class VideoDeletePermission(
                        val message: String = "no delete permission",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                    ) {
                        class Validator : ConstraintValidator<VideoDeletePermission, DeleteVideoPostCmd.Request> {
                            override fun isValid(value: DeleteVideoPostCmd.Request?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "MixedUnsupportedTarget.kt",
                """
                    package demo.application.validators.mixed
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [MixedUnsupportedTarget.Validator::class])
                    annotation class MixedUnsupportedTarget(
                        val message: String = "mixed unsupported target",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                    ) {
                        class Validator : ConstraintValidator<MixedUnsupportedTarget, Long> {
                            override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "ClassScalarValidator.kt",
                """
                    package demo.application.validators.classscalar
                    import jakarta.validation.Constraint
                    import jakarta.validation.ConstraintValidator
                    import jakarta.validation.ConstraintValidatorContext
                    import jakarta.validation.Payload
                    import kotlin.reflect.KClass

                    @Target(AnnotationTarget.CLASS)
                    @Retention(AnnotationRetention.RUNTIME)
                    @Constraint(validatedBy = [ClassScalarValidator.Validator::class])
                    annotation class ClassScalarValidator(
                        val message: String = "class scalar",
                        val groups: Array<KClass<*>> = [],
                        val payload: Array<KClass<out Payload>> = [],
                    ) {
                        class Validator : ConstraintValidator<ClassScalarValidator, Long> {
                            override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean = true
                        }
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        assertEquals("[]", json)
    }

    @Test
    fun `rejects legacy api payload Item response projection`() {
        val sources = listOf(
            SourceFile.kotlin(
                "LegacyPayload.kt",
                """
                    package demo.adapter.portal.api.payload.legacy
                    object LegacyPayload {
                        data class Request(val id: Long)
                        data class Item(val result: Boolean)
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()

        assertEquals("[]", json)
    }

    @Test
    fun `recovers stable fqns recursive containers array defaults and declaration order`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
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
                    "ComplexTypes.kt",
                    """
                        package demo.types

                        data class OrderId(val value: String)
                        enum class Status { READY, CLOSED }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "ComplexCmd.kt",
                    """
                        package demo.application.commands

                        import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                        import demo.types.OrderId
                        import demo.types.Status

                        @DesignBlockMetadata(
                            tag = "command",
                            packageName = "orders.commands",
                            name = "Complex",
                            family = "command",
                        )
                        object ComplexCmd {
                            data class Request(
                                val zeta: OrderId,
                                val aliases: Array<String?>? = emptyArray(),
                                val mapping: Map<String, List<Status?>>,
                                val details: Details,
                                val alpha: Int,
                            )

                            data class Details(
                                val second: String,
                                val first: Long,
                            )

                            data class Response(
                                val later: Boolean,
                                val earlier: String,
                            )
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val complex = findObject(extractTopLevelObjects(json), "command", "Complex")

        assertTrue(complex.contains("\"name\":\"zeta\",\"type\":\"demo.types.OrderId\""))
        assertTrue(complex.contains("\"name\":\"aliases\",\"type\":\"Array<String?>?\",\"defaultValue\":\"emptyArray()\""))
        assertTrue(complex.contains("\"name\":\"mapping\",\"type\":\"Map<String,List<demo.types.Status?>>\""))
        assertTrue(complex.contains("\"name\":\"details\",\"type\":\"demo.application.commands.ComplexCmd.Details\""))
        assertOrdered(
            complex,
            "\"name\":\"zeta\"",
            "\"name\":\"aliases\"",
            "\"name\":\"mapping\"",
            "\"name\":\"details\"",
            "\"name\":\"details.second\"",
            "\"name\":\"details.first\"",
            "\"name\":\"alpha\"",
            "\"name\":\"later\"",
            "\"name\":\"earlier\"",
        )
    }

    @Test
    fun `rejects primitive arrays from recovered tactical fields`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            listOf(
                SourceFile.kotlin(
                    "DesignBlockMetadata.kt",
                    """
                        package com.only4.cap4k.analysis.metadata

                        annotation class DesignBlockMetadata(
                            val tag: String,
                            val name: String,
                            val packageName: String,
                            val family: String = "",
                        )
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "PrimitiveArrayCmd.kt",
                    """
                        package demo.application.commands

                        @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                            tag = "command",
                            packageName = "orders.commands",
                            name = "PrimitiveArray",
                            family = "command",
                        )
                        object PrimitiveArrayCmd {
                            data class Request(val values: IntArray)
                            data class Response(val accepted: Boolean)
                        }
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(messages.contains("unsupported IR design field type kotlin.IntArray"))
    }

    @Test
    fun `recovers page derived fields only from the exact framework page contract`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
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
                    "PageRequest.kt",
                    """
                        package com.only4.cap4k.ddd.core.application.query

                        interface PageRequest {
                            val pageNum: Int
                            val pageSize: Int
                        }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "PageQueries.kt",
                    """
                        package demo.application.queries

                        import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
                        import com.only4.cap4k.ddd.core.application.query.PageRequest

                        @DesignBlockMetadata(
                            tag = "query",
                            packageName = "orders.queries",
                            name = "PagedOrders",
                            family = "query",
                            variant = "page",
                        )
                        object PagedOrdersQry {
                            data class Request(
                                override val pageNum: Int = 1,
                                override val pageSize: Int = 10,
                                val keyword: String? = null,
                            ) : PageRequest
                            data class Response(val orderId: Long)
                        }

                        @DesignBlockMetadata(
                            tag = "query",
                            packageName = "orders.queries",
                            name = "NamedLikePage",
                            family = "query",
                        )
                        object NamedLikePageQry {
                            data class Request(
                                val pageNum: Int,
                                val pageSize: Int,
                                val keyword: String,
                            )
                            data class Response(val orderId: Long)
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val objects = extractTopLevelObjects(outputDir.resolve("design-elements.json").toFile().readText())
        val paged = findObject(objects, "query", "PagedOrders")
        val namedLikePage = findObject(objects, "query", "NamedLikePage")

        assertTrue(paged.contains("\"fields\":[{\"name\":\"keyword\",\"type\":\"String?\",\"defaultValue\":\"null\"}]"))
        assertFalse(paged.contains("\"name\":\"pageNum\""))
        assertFalse(paged.contains("\"name\":\"pageSize\""))
        assertTrue(namedLikePage.contains("\"fields\":[{\"name\":\"pageNum\",\"type\":\"Int\"}"))
        assertTrue(namedLikePage.contains("{\"name\":\"pageSize\",\"type\":\"Int\"}"))
    }

    @Test
    fun `rejects page metadata when framework page defaults do not match`() {
        val messages = compileWithCap4kPluginExpectingFailure(
            listOf(
                SourceFile.kotlin(
                    "DesignBlockMetadata.kt",
                    """
                        package com.only4.cap4k.analysis.metadata

                        annotation class DesignBlockMetadata(
                            val tag: String,
                            val name: String,
                            val packageName: String,
                            val family: String = "",
                            val variant: String = "",
                        )
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "PageRequest.kt",
                    """
                        package com.only4.cap4k.ddd.core.application.query
                        interface PageRequest {
                            val pageNum: Int
                            val pageSize: Int
                        }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "InvalidPageQry.kt",
                    """
                        package demo.application.queries

                        @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                            tag = "query",
                            packageName = "orders.queries",
                            name = "InvalidPage",
                            family = "query",
                            variant = "page",
                        )
                        object InvalidPageQry {
                            data class Request(
                                override val pageNum: Int = 1,
                                override val pageSize: Int = 20,
                            ) : com.only4.cap4k.ddd.core.application.query.PageRequest
                            data class Response(val orderId: Long)
                        }
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(messages.contains("page design block query InvalidPage pageSize must default to 10"))
    }

    @Test
    fun `fails fast when event metadata conflicts with runtime annotations`() {
        val annotationSource = SourceFile.kotlin(
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
        )
        val domainMessages = compileWithCap4kPluginExpectingFailure(
            listOf(
                annotationSource,
                SourceFile.kotlin(
                    "DomainEvent.kt",
                    """
                        package com.only4.cap4k.ddd.core.domain.event.annotation
                        annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "ConflictingDomainEvent.kt",
                    """
                        package demo.domain.events

                        @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                            tag = "domain_event",
                            packageName = "orders.events",
                            name = "OrderChanged",
                            eventName = "order.changed.v1",
                            family = "domain-event",
                        )
                        @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(
                            value = "order.changed.v2",
                            persist = true,
                        )
                        data class OrderChanged(val orderId: Long)
                    """.trimIndent(),
                ),
            ),
        )
        val missingPersistedNameMessages = compileWithCap4kPluginExpectingFailure(
            listOf(
                annotationSource,
                SourceFile.kotlin(
                    "DomainEvent.kt",
                    """
                        package com.only4.cap4k.ddd.core.domain.event.annotation
                        annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "UnnamedPersistedEvent.kt",
                    """
                        package demo.domain.events

                        @com.only4.cap4k.analysis.metadata.DesignBlockMetadata(
                            tag = "domain_event",
                            packageName = "orders.events",
                            name = "UnnamedPersisted",
                            family = "domain-event",
                        )
                        @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(persist = true)
                        data class UnnamedPersisted(val orderId: Long)
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(domainMessages.contains("domain-event metadata/runtime eventName conflict"))
        assertTrue(
            missingPersistedNameMessages.contains("domain-event runtime annotation on demo.domain.events.UnnamedPersisted must declare a non-blank event name"),
        )
    }

    @Test
    fun `rejects integration event without event name in role package`() {
        val sources = listOf(
            SourceFile.kotlin(
                "IntegrationEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.application.event.annotation
                    annotation class IntegrationEvent(val value: String = "")
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "MissingEventNameIntegrationEvent.kt",
                """
                    package demo.application.subscribers.integration.inbound.media

                    @com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
                    data class MissingEventNameIntegrationEvent(val externalTaskId: String)
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()

        assertEquals("[]", json)
    }

    private fun extractTopLevelObjects(json: String): List<String> {
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
            if (inString) return@forEachIndexed
            when (ch) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(json.substring(start, index + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun findObject(objects: List<String>, tag: String, name: String): String {
        return objects.firstOrNull { it.contains("\"tag\":\"$tag\"") && it.contains("\"name\":\"$name\"") }
            ?: error("Missing element tag=$tag name=$name")
    }

    private fun assertOrdered(haystack: String, vararg needles: String) {
        val positions = needles.map { needle ->
            haystack.indexOf(needle).also { position ->
                assertTrue(position >= 0, "Missing '$needle' in $haystack")
            }
        }
        assertEquals(positions.sorted(), positions)
    }
}
