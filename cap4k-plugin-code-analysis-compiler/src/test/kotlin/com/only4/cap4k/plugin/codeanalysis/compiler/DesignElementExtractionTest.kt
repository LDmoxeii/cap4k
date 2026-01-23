package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementExtractionTest {
    @Test
    fun `emits design-elements json from request and payload`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam<T>"
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
                        interface Converter {
                            companion object
                        }
                    }
                """.trimIndent()
            )
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val objects = extractTopLevelObjects(json)
        assertTrue(json.contains("\"tag\":\"cmd\""))
        assertTrue(json.contains("\"name\":\"IssueToken\""))
        assertTrue(json.contains("\"name\":\"SubmitOrder\""))
        assertTrue(json.contains("\"cmdValue\""))
        assertTrue(json.contains("\"cmdResult\""))
        assertTrue(json.contains("\"tag\":\"qry\""))
        assertTrue(json.contains("\"name\":\"AutoLogin\""))
        assertTrue(json.contains("\"sessionToken\""))
        assertTrue(json.contains("\"tag\":\"cli\""))
        assertTrue(json.contains("\"name\":\"CaptchaGen\""))
        assertTrue(json.contains("\"cliAccount\""))
        assertTrue(json.contains("\"captchaId\""))
        assertTrue(json.contains("\"account.accountNumber\""))
        assertTrue(json.contains("\"tag\":\"de\""))
        assertTrue(json.contains("\"name\":\"UserCreated\""))
        assertTrue(json.contains("\"entity\":\"User\""))
        assertTrue(json.contains("\"persist\":true"))
        val autoLogin = findObject(objects, "qry", "AutoLogin")
        assertTrue(autoLogin.contains("\"requestFields\":[]"))
        val fireAndForget = findObject(objects, "cmd", "FireAndForget")
        assertTrue(fireAndForget.contains("\"responseFields\":[]"))
        val topCmd = findObject(objects, "cmd", "Top")
        assertTrue(topCmd.contains("\"package\":\"\""))
        val topQry = findObject(objects, "qry", "Top")
        assertTrue(topQry.contains("\"package\":\"\""))
        val topCli = findObject(objects, "cli", "Top")
        assertTrue(topCli.contains("\"package\":\"\""))
        val userCreated = findObject(objects, "de", "UserCreated")
        assertTrue(userCreated.contains("\"package\":\"user\""))
        assertTrue(userCreated.contains("\"responseFields\":[]"))
        assertFalse(json.contains("\"package\":\"account.BatchSaveAccountList.Converter\""))
        assertFalse(json.contains("\"name\":\"companion\""))
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
