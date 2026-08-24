package io.sebi.househelper

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LightIntegrationTest {

    @Test
    fun `tool changes are visible through the lights HTTP API`() {
        val service = LightService()
        val tools = LightTools(service)
        val mockMvc = MockMvcBuilders.standaloneSetup(LightController(service)).build()

        tools.setLightPower("living-room", true)
        tools.setLightBrightness("living-room", 42)
        tools.setLightColor("living-room", red = 10, green = 20, blue = 30)

        mockMvc.get("/api/lights")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value("living-room") }
                jsonPath("$[0].on") { value(true) }
                jsonPath("$[0].brightness") { value(42) }
                jsonPath("$[0].color.red") { value(10) }
                jsonPath("$[0].color.green") { value(20) }
                jsonPath("$[0].color.blue") { value(30) }
            }
    }

}