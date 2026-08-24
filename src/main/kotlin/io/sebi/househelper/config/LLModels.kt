package io.sebi.househelper.config

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

// GPT-5.6 Luna isn't in ai.koog's OpenAIModels catalog yet, so it's defined here directly.
// Unlike AnthropicLLMClient, OpenAILLMClient sends model.id straight through without
// validating it against a built-in catalog, so no client override bean is needed here.
val GPT56Luna: LLModel = LLModel(
    provider = LLMProvider.OpenAI,
    id = "gpt-5.6-luna",
    capabilities = listOf(
        LLMCapability.Completion,
        LLMCapability.Speculation,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Vision.Image,
        LLMCapability.Document,
        LLMCapability.MultipleChoices,
        LLMCapability.OpenAIEndpoint.Responses,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.Thinking,
    ),
    contextLength = 1_050_000,
    maxOutputTokens = 128_000,
)
