package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementExtractionTest {
    @Test
    fun `emits design-elements json from request and payload`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam"
            ),
            SourceFile.kotlin(
                "DomainEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.event.annotation
                    annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
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
                "IssueTokenCmd.kt",
                """
                    package demo.application.commands.authorize
                    class IssueTokenCmd : com.only4.cap4k.ddd.core.application.RequestParam {
                        data class Request(val userId: Long, val note: String = "x")
                        data class Response(val token: String)
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UserCreated.kt",
                """
                    package demo.domain.event
                    @com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent(persist = true)
                    @com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate(aggregate = "User", type = "domain-event")
                    data class UserCreated(val userId: Long)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "BatchSaveAccountList.kt",
                """
                    package demo.adapter.portal.api.payload.account
                    object BatchSaveAccountList {
                        data class Request(val globalId: String, val account: AccountInfo)
                        data class Item(val result: Boolean)
                        data class AccountInfo(val accountNumber: String)
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        assertTrue(json.contains("\"tag\":\"cmd\""))
        assertTrue(json.contains("\"name\":\"IssueToken\""))
        assertTrue(json.contains("\"account.accountNumber\""))
        assertTrue(json.contains("\"tag\":\"de\""))
        assertTrue(json.contains("\"name\":\"UserCreated\""))
        assertTrue(json.contains("\"entity\":\"User\""))
        assertTrue(json.contains("\"persist\":true"))
    }
}
