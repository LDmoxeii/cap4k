package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementExtractionTest {
    @Test
    fun `recovers and merges design blocks from BuildingBlock annotations`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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
        assertTrue(findOrder.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\",\"nullable\":false}"))
        assertTrue(findOrder.contains("\"name\":\"keyword\",\"type\":\"String\",\"nullable\":true,\"defaultValue\":\"null\""))
        assertTrue(findOrder.contains("\"resultFields\":[{\"name\":\"orderNo\",\"type\":\"String\",\"nullable\":false}]"))
        assertFalse(json.contains("\"desc\""))
        assertFalse(json.contains("\"traits\""))
        assertFalse(json.contains("\"role\""))
        assertFalse(json.contains("\"entity\""))
        assertFalse(json.contains("\"requestFields\""))
        assertFalse(json.contains("\"responseFields\""))
    }

    @Test
    fun `recovers generated outer BuildingBlock command request and response`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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

        assertTrue(submitOrder.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\",\"nullable\":false}"))
        assertTrue(submitOrder.contains("\"name\":\"note\",\"type\":\"String\",\"nullable\":true,\"defaultValue\":\"null\""))
        assertTrue(submitOrder.contains("\"resultFields\":[{\"name\":\"accepted\",\"type\":\"Boolean\",\"nullable\":false}]"))
    }

    @Test
    fun `recovers generated outer BuildingBlock saga artifact without DTO metadata`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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
                "RecoverPaymentSaga.kt",
                """
                    package demo.application.sagas.payment

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
                        tag = "saga",
                        packageName = "payment.recovery",
                        name = "RecoverPayment",
                        description = "Recover payment",
                        aggregates = ["Payment"],
                        family = "saga",
                    )
                    object RecoverPaymentSaga {
                        data class Request(
                            val paymentId: Long,
                            val attempt: Attempt,
                        )

                        data class Response(
                            val recovered: Boolean,
                            val nextAction: String? = null,
                        )

                        data class Attempt(
                            val number: Int,
                            val reason: String,
                        )
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val recoverPayment = findObject(extractTopLevelObjects(json), "saga", "RecoverPayment")

        assertTrue(recoverPayment.contains("\"artifacts\":[{\"family\":\"saga\"}]"))
        assertTrue(recoverPayment.contains("\"fields\":[]"))
        assertTrue(recoverPayment.contains("\"resultFields\":[]"))
        assertFalse(recoverPayment.contains("paymentId"))
        assertFalse(recoverPayment.contains("attempt"))
        assertFalse(recoverPayment.contains("recovered"))
        assertFalse(recoverPayment.contains("nextAction"))
    }

    @Test
    fun `rejects conflicting BuildingBlock shared metadata`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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
            messages.contains("conflicting BuildingBlock metadata for query order.read FindOrder: description"),
        )
    }

    @Test
    fun `rejects BuildingBlock annotations with blank required identity`() {
        val annotationSource = SourceFile.kotlin(
            "BuildingBlock.kt",
            """
                package com.only4.cap4k.ddd.core.annotation

                annotation class BuildingBlock(
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

                        import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                        @BuildingBlock(
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

                        import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                        @BuildingBlock(
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
            blankTagMessages.contains("BuildingBlock annotation on demo.application.queries.order.BlankTagBlock must declare non-blank tag"),
        )
        assertTrue(
            blankFamilyMessages.contains("BuildingBlock annotation on demo.application.queries.order.BlankFamilyBlock must declare non-blank family"),
        )
    }

    @Test
    fun `query handler dependencies do not become recovered fields`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    @BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    interface CustomerReadRepository
                    interface ClockProvider

                    @BuildingBlock(
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
        assertTrue(findCustomer.contains("\"fields\":[{\"name\":\"customerId\",\"type\":\"Long\",\"nullable\":false}]"))
        assertTrue(findCustomer.contains("\"resultFields\":[{\"name\":\"displayName\",\"type\":\"String\",\"nullable\":false}]"))
        assertFalse(findCustomer.contains("repository"))
        assertFalse(findCustomer.contains("clockProvider"))
    }

    @Test
    fun `generated domain event recovery excludes synthetic entity constructor parameter`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock
                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    class Order

                    @BuildingBlock(
                        tag = "domain_event",
                        packageName = "order.events",
                        name = "OrderCreated",
                        description = "order created",
                        aggregates = ["Order"],
                        family = "domain-event",
                    )
                    @DomainEvent(persist = true)
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
        assertTrue(orderCreated.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\",\"nullable\":false}"))
        assertTrue(orderCreated.contains("\"name\":\"reason\",\"type\":\"String\",\"nullable\":true,\"defaultValue\":\"null\""))
        assertFalse(orderCreated.contains("\"name\":\"entity\""))
        assertFalse(json.contains("\"requestFields\""))
        assertFalse(json.contains("\"responseFields\""))
    }

    @Test
    fun `integration subscriber dependencies do not conflict with event body fields`() {
        val sources = listOf(
            SourceFile.kotlin(
                "BuildingBlock.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation

                    annotation class BuildingBlock(
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
                    annotation class IntegrationEvent(val value: String = "", val subscriber: String = "[none]")
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "PaymentReceivedIntegrationEvent.kt",
                """
                    package demo.application.subscribers.integration.inbound.payment

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock
                    import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent

                    @IntegrationEvent("demo.payment.received")
                    @BuildingBlock(
                        tag = "integration_event",
                        packageName = "payment.integration",
                        name = "PaymentReceived",
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

                    import com.only4.cap4k.ddd.core.annotation.BuildingBlock

                    interface PaymentCommandPort
                    interface AuditTrail

                    @BuildingBlock(
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
        assertTrue(paymentReceived.contains("\"fields\":[{\"name\":\"paymentId\",\"type\":\"String\",\"nullable\":false}"))
        assertTrue(paymentReceived.contains("\"name\":\"amount\",\"type\":\"Long\",\"nullable\":false}"))
        assertTrue(paymentReceived.contains("\"resultFields\":[]"))
        assertFalse(paymentReceived.contains("commandPort"))
        assertFalse(paymentReceived.contains("auditTrail"))
    }

    @Test
    fun `emits design-elements json from request and payload`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam<T>"
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
                    annotation class IntegrationEvent(val value: String = "", val subscriber: String = "[none]")
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "AggregateElement.kt",
                """
                    package com.only4.cap4k.ddd.core.annotation
                    annotation class AggregateElement(
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
                    class IssueTokenCmd : com.only4.cap4k.ddd.core.application.RequestParam<IssueTokenCmd.Response> {
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
                        data class Request(val cmdValue: String) : com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val cmdResult: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "AutoLoginQry.kt",
                """
                    package demo.application.queries.session
                    object AutoLoginQry {
                        class Request : com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val sessionToken: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CaptchaGenCli.kt",
                """
                    package demo.application.distributed.clients.auth
                    object CaptchaGenCli {
                        data class Request(val cliAccount: String) : com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val captchaId: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "TopCmd.kt",
                """
                    package demo.application.commands
                    object TopCmd {
                        data class Request(val id: Long) : com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "TopQry.kt",
                """
                    package demo.application.queries
                    object TopQry {
                        class Request : com.only4.cap4k.ddd.core.application.RequestParam<Response>
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
                            com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val page: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "TopCli.kt",
                """
                    package demo.application.distributed.clients
                    object TopCli {
                        data class Request(val token: String) : com.only4.cap4k.ddd.core.application.RequestParam<Response>
                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "FireAndForgetCmd.kt",
                """
                    package demo.application.commands.notice
                    object FireAndForgetCmd {
                        data class Request(val message: String) : com.only4.cap4k.ddd.core.application.RequestParam<Unit>
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
                        subscriber = "\${'$'}{spring.application.name:}"
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
                        subscriber = "[none]"
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
    fun `rejects integration event without event name in role package`() {
        val sources = listOf(
            SourceFile.kotlin(
                "IntegrationEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.application.event.annotation
                    annotation class IntegrationEvent(val value: String = "", val subscriber: String = "[none]")
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
}
