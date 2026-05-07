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
    fun `command handler calling aggregate member method keeps exact entity method edges`() {
        val rels = compileRelationships(
            categorySources(
                categoryBody = """
                    @Aggregate(aggregate = "Category", type = "entity", root = true)
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
    fun `supported stable defaults survive request projection into design-elements json`() {
        val json = compileDesignElements(
            stableDefaultSources(
                channelsType = "Set<CaptchaChannel>",
                channelsDefaultExpression = "emptySet()",
            ),
        )

        val issueCaptcha = findDesignElement(json, tag = "command", name = "IssueCaptcha")

        assertTrue(issueCaptcha.contains(""""name":"note","type":"String","nullable":true,"defaultValue":"null""""))
        assertTrue(issueCaptcha.contains(""""name":"title","type":"String","nullable":false,"defaultValue":"\"inline\"""""))
        assertTrue(issueCaptcha.contains(""""name":"attempt","type":"Int","nullable":false,"defaultValue":"1""""))
        assertTrue(issueCaptcha.contains(""""name":"enabled","type":"Boolean","nullable":false,"defaultValue":"true""""))
        assertTrue(issueCaptcha.contains(""""name":"tags","type":"List<String>","nullable":false,"defaultValue":"emptyList()""""))
        assertTrue(issueCaptcha.contains(""""name":"channels","type":"Set<CaptchaChannel>","nullable":false,"defaultValue":"emptySet()""""))
        assertTrue(issueCaptcha.contains(""""name":"metadata","type":"Map<String,String>","nullable":false,"defaultValue":"emptyMap()""""))
        assertTrue(issueCaptcha.contains(""""name":"preferredChannel","type":"CaptchaChannel","nullable":false,"defaultValue":"demo.application.commands.auth.CaptchaChannel.INLINE""""))
        assertTrue(issueCaptcha.contains(""""name":"policy","type":"CaptchaPolicy","nullable":false,"defaultValue":"demo.application.commands.auth.CaptchaPolicy""""))
        assertTrue(issueCaptcha.contains(""""name":"referenceTitle","type":"String","nullable":false,"defaultValue":"demo.application.shared.defaults.SHARED_FIELD_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"externalPreferredChannel","type":"SharedCaptchaChannel","nullable":false,"defaultValue":"demo.application.shared.defaults.SharedCaptchaChannel.IMAGE""""))
        assertTrue(issueCaptcha.contains(""""name":"externalPolicy","type":"SharedCaptchaPolicy","nullable":false,"defaultValue":"demo.application.shared.defaults.SharedCaptchaPolicy""""))
        assertTrue(issueCaptcha.contains(""""name":"topLevelReferenceTitle","type":"String","nullable":false,"defaultValue":"demo.application.shared.defaults.TOP_LEVEL_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"topLevelGetterReferenceTitle","type":"String","nullable":false,"defaultValue":"demo.application.shared.defaults.TOP_LEVEL_GETTER_DEFAULT_TITLE""""))
        assertTrue(issueCaptcha.contains(""""name":"objectGetterReferenceTitle","type":"String","nullable":false,"defaultValue":"demo.application.shared.defaults.SharedGetterDefaults.OBJECT_DEFAULT_TITLE""""))
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field channels",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field referenceTitle",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field privateReferenceTitle",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field referenceTitle",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field privatePolicy",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field privatePreferredChannel",
            ),
        )
    }

    @Test
    fun `multi statement composite defaults fail request projection explicitly`() {
        val collector = DesignElementCollector(Cap4kOptions(), emptyMap())
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
        val collector = DesignElementCollector(Cap4kOptions(), emptyMap())
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
        val collector = DesignElementCollector(Cap4kOptions(), emptyMap())
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field dynamicTopLevelFieldTitle",
            ),
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
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field dynamicJavaFieldTitle",
            ),
        )
    }

    private fun compileRelationships(sources: List<SourceFile>): List<RelationshipView> {
        val outputDir = compileWithCap4kPlugin(sources)
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
                "RequestParam.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestParam<RESULT : Any>
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "RequestHandler.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestHandler<REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> {
                        fun exec(request: REQUEST): RESPONSE
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    import com.only4.cap4k.ddd.core.application.RequestHandler
                    import com.only4.cap4k.ddd.core.application.RequestParam

                    interface Command<PARAM : RequestParam<RESULT>, RESULT : Any> : RequestHandler<PARAM, RESULT> {
                        override fun exec(request: PARAM): RESULT
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Aggregate.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.aggregate.annotation

                    annotation class Aggregate(
                        val aggregate: String = "",
                        val type: String = "",
                        val root: Boolean = false
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

                    import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
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

                    import com.only4.cap4k.ddd.core.application.RequestParam
                    import com.only4.cap4k.ddd.core.application.command.Command
                    import demo.domain.aggregates.category.Category
                    $behaviorImport

                    class UpdateCategorySortCmd(val sort: Int) : RequestParam<UpdateCategorySortCmd.Response> {
                        class Response

                        class Handler : Command<UpdateCategorySortCmd, Response> {
                            override fun exec(request: UpdateCategorySortCmd): Response {
                                val category = Category()
                                category.changeSort(request.sort)
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
                "RequestParam.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestParam<RESULT : Any>
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
                "IssueCaptchaCmd.kt",
                """
                    package demo.application.commands.auth

                    import com.only4.cap4k.ddd.core.application.RequestParam
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
                        ) : RequestParam<Response>

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
            @Aggregate(aggregate = "Category", type = "entity", root = true)
            class Category
        """
    }
}
