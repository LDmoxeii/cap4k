package com.acme.demo.adapter.mvc

import com.acme.demo.domain.shared.ids.AuthorId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val GENERATED_AUTHOR_ID = "019c0000-0000-7000-8000-000000000001"
private const val INVALID_UUID4 = "550e8400-e29b-41d4-a716-446655440000"

class GeneratedStrongIdSpringMvcBindingTest {
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(GeneratedStrongIdController()).build()

    @Test
    fun `generated ref id binds from mvc path and query inputs`() {
        mockMvc.perform(get("/generated/authors/$GENERATED_AUTHOR_ID"))
            .andExpect(status().isOk)
            .andExpect(content().string(GENERATED_AUTHOR_ID))

        mockMvc.perform(
            get("/generated/authors")
                .param("authorId", GENERATED_AUTHOR_ID),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(GENERATED_AUTHOR_ID))
    }

    @Test
    fun `generated ref id mvc binding preserves semantic validation`() {
        val result = mockMvc.perform(
            get("/generated/authors")
                .param("authorId", INVALID_UUID4),
        )
            .andExpect(status().isBadRequest)
            .andReturn()

        val messages = generateSequence<Throwable>(result.resolvedException) { it.cause }
            .mapNotNull { it.message }
            .toList()
        assertTrue(messages.any { it.contains("AuthorId must be a UUIDv7 value") })
    }

    @RestController
    class GeneratedStrongIdController {
        @GetMapping("/generated/authors/{authorId}")
        fun byPath(@PathVariable("authorId") authorId: AuthorId): String = authorId.toString()

        @GetMapping("/generated/authors")
        fun byQuery(@RequestParam("authorId") authorId: AuthorId): String = authorId.toString()
    }
}
