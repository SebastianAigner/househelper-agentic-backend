package com.example.koogboot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightServiceTest {

    private val service = LightService()

    @Test
    fun `provides mocked lights and returns defensive snapshots`() {
        val lights = service.getLights()

        assertEquals(3, lights.size)
        assertEquals(listOf("living-room", "kitchen", "bedroom"), lights.map { it.id })
        assertFalse(lights.first().on)
    }

    @Test
    fun `updates power brightness and color while preserving other state`() {
        service.setPower("kitchen", true)
        service.setBrightness("kitchen", 65)
        val updated = service.setColor("kitchen", red = 12, green = 34, blue = 56)

        assertTrue(updated.on)
        assertEquals(65, updated.brightness)
        assertEquals(RgbColor(12, 34, 56), updated.color)
        assertFalse(service.getLight("bedroom").on)
    }

    @Test
    fun `rejects unknown lights and values outside supported ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.setPower("garage", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.setBrightness("bedroom", 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.setColor("bedroom", red = -1, green = 0, blue = 256)
        }
    }
}