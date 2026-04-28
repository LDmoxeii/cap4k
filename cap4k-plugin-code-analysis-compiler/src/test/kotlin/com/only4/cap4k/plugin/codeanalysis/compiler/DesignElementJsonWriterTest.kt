package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementJsonWriterTest {
    @Test
    fun `serializes design elements with fields and defaults`() {
        val elements = listOf(
            DesignElement(
                tag = "api_payload",
                `package` = "account",
                name = "batchSaveAccountList",
                desc = "",
                aggregates = emptyList(),
                entity = null,
                persist = true,
                requestFields = listOf(
                    DesignField("globalId", "String", false, "0"),
                    DesignField("account.accountNumber", "String", false, null)
                ),
                responseFields = listOf(DesignField("result", "Boolean", false, null))
            )
        )

        val json = DesignElementJsonWriter().write(elements)
        assertTrue(json.contains("\"name\":\"batchSaveAccountList\""))
        assertTrue(json.contains("\"defaultValue\":\"0\""))
        assertTrue(json.contains("\"nullable\":false"))
        assertTrue(json.contains("\"persist\":true"))
        assertTrue(json.contains("\"account.accountNumber\""))
    }

    @Test
    fun `serializes validator projection fields`() {
        val elements = listOf(
            DesignElement(
                tag = "validator",
                `package` = "danmuku",
                name = "DanmukuDeletePermission",
                desc = "delete permission",
                message = "no\rdelete\tpermission\b\u000C",
                targets = listOf("CLASS"),
                valueType = "Any",
                parameters = listOf(
                    DesignParameter(
                        name = "danmukuIdField",
                        type = "String",
                        nullable = false,
                        defaultValue = "danmuku\r\t\b\u000C",
                    ),
                ),
            )
        )

        val json = DesignElementJsonWriter().write(elements)

        assertTrue(json.contains("\"tag\":\"validator\""))
        assertTrue(json.contains("\"message\":\"no\\rdelete\\tpermission\\b\\f\""))
        assertTrue(json.contains("\"targets\":[\"CLASS\"]"))
        assertTrue(json.contains("\"valueType\":\"Any\""))
        assertTrue(json.contains("\"parameters\":[{\"name\":\"danmukuIdField\""))
        assertTrue(json.contains("\"defaultValue\":\"danmuku\\r\\t\\b\\f\""))
    }
}
