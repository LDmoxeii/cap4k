package com.acme.endpoint.adapter

import com.acme.endpoint.EndpointHttpFixtureApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [EndpointHttpFixtureApplication::class])
@AutoConfigureMockMvc
class EndpointHttpBindingApplicationTest(
    @Autowired private val mvc: MockMvc,
) {
    @Test
    fun `ordinary json special redirect and bad requests use real spring routes`() {
        mvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerName":"Ada"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Endpoint-Filter", "applied"))
            .andExpect(content().json("""{"bookingId":"booking-ada"}"""))

        mvc.perform(get("/file/getResource").queryParam("sourceName", "cover.png"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://cdn.example.test/cover.png"))
            .andExpect(content().string(""))

        mvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerName":""}"""),
        ).andExpect(status().isBadRequest)

        mvc.perform(get("/file/getResource"))
            .andExpect(status().isBadRequest)
    }
}
