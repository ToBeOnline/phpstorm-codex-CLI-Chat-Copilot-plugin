package com.codex.phpstorm.chat

enum class Role(val apiValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");
}

data class ChatMessage(
    val role: Role,
    val content: String,
    val name: String? = null
)
