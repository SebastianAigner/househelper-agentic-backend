package io.sebi.househelper.sample

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import org.springframework.stereotype.Service

@Service
class KitchenService {

    fun getFridgeContents(): String = "milk, eggs, butter, cheese, leftover pasta"

    fun getPantryContents(): String = "rice, pasta, canned tomatoes, olive oil, flour"
}

@LLMDescription("Tools for looking up kitchen contents")
class KitchenToolSet(
    private val kitchenService: KitchenService
) : ToolSet {

    @Tool
    @LLMDescription("Get the current contents of the fridge")
    fun getFridgeContents(): String =
        kitchenService.getFridgeContents()

    @Tool
    @LLMDescription("Get the current contents of the pantry")
    fun getPantryContents(): String =
        kitchenService.getPantryContents()
}
