package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementJsonWriterTest {
    @Test
    fun `serializes design elements with fields and defaults`() {
        val elements = listOf(
            DesignElement(
                tag = "payload",
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
}
