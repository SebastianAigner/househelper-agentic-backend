package com.example.koogboot

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ChatRequest(val message: String)
data class ChatResponse(val response: String)

@RestController
@RequestMapping("/api/chat")
class ChatController(private val chatService: ChatService) {

    @PostMapping
    suspend fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        return try {
            ResponseEntity.ok(ChatResponse(chatService.chat(request.message)))
        } catch (e: Exception) {
            ResponseEntity.internalServerError()
                .body(ChatResponse("Error processing request: ${e.message}"))
        }
    }
}
