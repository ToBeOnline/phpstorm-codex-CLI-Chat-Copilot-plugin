package com.codex.phpstorm.settings

enum class CodexBackend(val displayName: String) {
    OPENAI_API("OpenAI API"),
    CODEX_CLI("Codex CLI");

    override fun toString(): String = displayName
}
