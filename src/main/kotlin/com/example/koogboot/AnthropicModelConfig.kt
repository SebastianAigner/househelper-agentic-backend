package com.example.koogboot

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.spring.prompt.executor.clients.anthropic.AnthropicKoogProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class AnthropicModelConfig {

    // The default AnthropicLLMClient only resolves the models built into ai.koog's catalog
    // (its modelVersionsMap lookup rejects anything else). Overriding it with @Primary lets
    // the starter's own anthropicExecutor bean pick this client up transparently.
    @Bean
    @Primary
    fun pinnedAnthropicLLMClient(properties: AnthropicKoogProperties): AnthropicLLMClient =
        AnthropicLLMClient(
            apiKey = properties.apiKey,
            settings = AnthropicClientSettings(
                baseUrl = properties.baseUrl,
                modelVersionsMap = mapOf(ClaudeSonnet5 to "claude-sonnet-5"),
            ),
        )
}
