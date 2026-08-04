package com.only4.cap4k.plugin.pipeline.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineJsonTest {
    @Test
    fun `compact output sorts map keys preserves array order and does not HTML escape`() {
        val mapper = PipelineJson.newMapper()

        val json = mapper.writeValueAsString(
            linkedMapOf(
                "z" to listOf("second", "first"),
                "generic" to "List<com.example.Item>",
                "a" to 1,
            ),
        )

        assertEquals(
            """{"a":1,"generic":"List<com.example.Item>","z":["second","first"]}""",
            json,
        )
    }

    @Test
    fun `null inclusion is an explicit boundary choice`() {
        val value = linkedMapOf("present" to "value", "absent" to null)

        assertFalse(PipelineJson.newMapper().writeValueAsString(value).contains("absent"))
        assertTrue(PipelineJson.newMapper(includeNulls = true).writeValueAsString(value).contains("\"absent\":null"))
    }

    @Test
    fun `lowercase enum wire names remain an explicit boundary choice`() {
        val mapper = PipelineJson.newMapper(lowercaseEnums = true)

        assertEquals("\"second_value\"", mapper.writeValueAsString(SampleEnum.SECOND_VALUE))
        assertEquals(SampleEnum.SECOND_VALUE, mapper.readValue("\"SECOND_VALUE\"", SampleEnum::class.java))
    }

    private enum class SampleEnum {
        FIRST_VALUE,
        SECOND_VALUE,
    }

    @Test
    fun `pretty output uses stable LF line endings`() {
        val writer = PipelineJson.prettyWriter(PipelineJson.newMapper())
        val json = writer.writeValueAsString(mapOf("values" to listOf(1, 2)))

        assertTrue(json.contains('\n'))
        assertFalse(json.contains("\r\n"))
        assertEquals(
            "{\n  \"emptyArray\": [],\n  \"emptyObject\": {}\n}",
            writer.writeValueAsString(
                mapOf("emptyArray" to emptyList<Any>(), "emptyObject" to emptyMap<String, Any>()),
            ),
        )
    }
}
