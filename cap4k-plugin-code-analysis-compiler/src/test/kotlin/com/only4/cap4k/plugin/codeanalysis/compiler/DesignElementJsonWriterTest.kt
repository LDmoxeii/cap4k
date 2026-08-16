package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignArtifact
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignElementJsonWriterTest {
    @Test
    fun `serializes public design block schema with fields artifacts and defaults`() {
        val elements = listOf(
            DesignElement(
                tag = "query",
                `package` = "account",
                name = "batchSaveAccountList",
                description = "batch save accounts",
                aggregates = emptyList(),
                persist = true,
                artifacts = listOf(
                    DesignArtifact(family = "query", variant = "page"),
                    DesignArtifact(family = "query-handler"),
                ),
                fields = listOf(
                    DesignField("globalId", "String", "0"),
                    DesignField("account.accountNumber", "String")
                ),
                resultFields = listOf(DesignField("result", "Boolean"))
            )
        )

        val json = DesignElementJsonWriter().write(elements)
        assertTrue(json.contains("\"name\":\"batchSaveAccountList\""))
        assertTrue(json.contains("\"description\":\"batch save accounts\""))
        assertTrue(json.contains("\"defaultValue\":\"0\""))
        assertFalse(json.contains("\"nullable\""))
        assertTrue(json.contains("\"persist\":true"))
        assertTrue(json.contains("\"artifacts\":[{\"family\":\"query\",\"variant\":\"page\"},{\"family\":\"query-handler\"}]"))
        assertTrue(json.contains("\"fields\":["))
        assertTrue(json.contains("\"resultFields\":["))
        assertTrue(json.contains("\"account.accountNumber\""))
        assertFalse(json.contains("\"desc\""))
        assertFalse(json.contains("\"traits\""))
        assertFalse(json.contains("\"role\""))
        assertFalse(json.contains("\"entity\""))
        assertFalse(json.contains("\"requestFields\""))
        assertFalse(json.contains("\"responseFields\""))
    }

    @Test
    fun `serializes integration event metadata`() {
        val elements = listOf(
            DesignElement(
                tag = "integration_event",
                `package` = "media.processing",
                name = "MediaProcessingCallbackIntegrationEvent",
                description = "media processing completed",
                eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                fields = listOf(DesignField("externalTaskId", "String")),
                resultFields = emptyList(),
            )
        )

        val json = DesignElementJsonWriter().write(elements)

        assertTrue(json.contains("\"tag\":\"integration_event\""))
        assertTrue(json.contains("\"description\":\"media processing completed\""))
        assertTrue(json.contains("\"eventName\":\"cap4k.reference.contentstudio.media-processing.succeeded\""))
        assertFalse(json.contains("\"role\""))
    }
}
