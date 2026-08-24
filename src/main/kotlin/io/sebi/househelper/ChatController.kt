package io.sebi.househelper

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.Writer

data class ChatRequest(val message: String)
data class ChatResponse(val response: String)

@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping
    suspend fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        return try {
            ResponseEntity.ok(ChatResponse(chatService.chat(request.message)))
        } catch (e: Exception) {
            ResponseEntity.internalServerError()
                .body(ChatResponse("Error processing request: ${e.message}"))
        }
    }

    @PostMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatSse(@RequestBody request: ChatRequest): ResponseEntity<StreamingResponseBody> {
        val body = StreamingResponseBody { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                runBlocking {
                    try {
                        chatService.streamChat(request.message) { event ->
                            when (event) {
                                is ChatStreamEvent.Text -> writer.sendSseEvent("text", event.text, objectMapper)
                                is ChatStreamEvent.ToolCall -> writer.sendSseEvent("tool", event.text, objectMapper)
                            }
                        }
                        writer.sendSseEvent("done", "", objectMapper)
                    } catch (e: Exception) {
                        writer.sendSseEvent("error", e.message ?: "Error processing request", objectMapper)
                    }
                }
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(body)
    }
}

internal fun Writer.sendSseEvent(event: String, data: String, objectMapper: ObjectMapper) {
    write("event: $event\n")
    write("data: ${objectMapper.writeValueAsString(data)}\n\n")
    flush()
}
