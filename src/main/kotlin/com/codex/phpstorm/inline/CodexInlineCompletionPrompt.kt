package com.codex.phpstorm.inline

import com.codex.phpstorm.client.ChatCompletionMessage
import com.intellij.psi.PsiFile

object CodexInlineCompletionPrompt {

    fun buildMessages(baseSystemPrompt: String, file: PsiFile, prefix: String, suffix: String): List<ChatCompletionMessage> {
        val system = buildString {
            val trimmedBase = baseSystemPrompt.trim()
            if (trimmedBase.isNotEmpty()) {
                append(trimmedBase)
                append("\n\n")
            }
            append(
                """
                You are a code completion engine.
                Return ONLY the code that should be inserted at the cursor.
                Do not include markdown, backticks, or explanations.
                Do not repeat or re-declare code that already exists after the cursor (see SUFFIX).
                Keep the completion concise (prefer <= 20 lines).
                """.trimIndent()
            )
        }

        val user = buildString {
            append("File: ")
            append(file.name)
            append('\n')
            append("Language: ")
            append(file.language.displayName)
            append("\n\n")
            append("PREFIX (before cursor):\n")
            append(prefix)
            append("\n\nSUFFIX (after cursor):\n")
            append(suffix)
        }

        return listOf(
            ChatCompletionMessage(role = "system", content = system),
            ChatCompletionMessage(role = "user", content = user)
        )
    }
}
