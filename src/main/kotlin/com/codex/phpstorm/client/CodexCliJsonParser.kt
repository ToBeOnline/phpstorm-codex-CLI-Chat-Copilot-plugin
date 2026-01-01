package com.codex.phpstorm.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object CodexCliJsonParser {

    data class Parsed(
        val threadId: String? = null,
        val lastAgentMessage: String? = null,
        val allAgentMessages: List<String> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonl: String): Parsed {
        var threadId: String? = null
        val messages = mutableListOf<String>()

        for (line in jsonl.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val obj = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: continue
            val type = obj.stringOrNull("type") ?: continue
            when (type) {
                "thread.started" -> threadId = obj.stringOrNull("thread_id") ?: threadId
                "item.completed" -> {
                    val item = obj["item"]?.jsonObject ?: continue
                    val itemType = item.stringOrNull("type") ?: continue
                    if (itemType == "agent_message") {
                        val text = item.stringOrNull("text")
                        if (!text.isNullOrBlank()) {
                            messages.add(text)
                        }
                    }
                }
            }
        }

        return Parsed(
            threadId = threadId,
            lastAgentMessage = messages.lastOrNull(),
            allAgentMessages = messages.toList()
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return (this[key] as? JsonPrimitive)?.content
    }
}
