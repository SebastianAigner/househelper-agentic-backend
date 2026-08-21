package com.example.koogboot

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringWriter

class ChatSseTest {

    @Test
    fun `writes named SSE events with JSON-escaped data`() {
        val writer = StringWriter()

        writer.sendSseEvent("tool", "setLightColor {\"id\":\"kitchen\"}\nnext", ObjectMapper())

        assertEquals(
            "event: tool\ndata: \"setLightColor {\\\"id\\\":\\\"kitchen\\\"}\\nnext\"\n\n",
            writer.toString(),
        )
    }
}