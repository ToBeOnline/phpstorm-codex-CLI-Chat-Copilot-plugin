package com.codex.phpstorm.actions

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

class CodexInlineCompletionDiagnosticsAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val settings = CodexSettingsState.getInstance().state

        val text = buildString {
            appendLine("Codex inline completions enabled: ${settings.inlineCompletionEnabled}")
            appendLine("Backend: ${settings.backend}")
            appendLine("OpenAI base URL: ${settings.apiBaseUrl.ifBlank { "(empty)" }}")
            appendLine("OpenAI API key: ${if (settings.apiKey.isBlank()) "(empty)" else "(set)" }")
            appendLine("Chat model (OpenAI): ${settings.model.ifBlank { "(empty)" }}")
            appendLine("Chat model (Codex CLI): ${settings.codexCliModel.ifBlank { "(empty)" }}")
            appendLine("Inline model (OpenAI): ${settings.inlineCompletionOpenAiModel.ifBlank { "(Same as Model)" }}")
            appendLine("Inline model (Codex CLI): ${settings.inlineCompletionCodexCliModel.ifBlank { "(Same as Model)" }}")
            appendLine("Inline temperature (OpenAI): ${settings.inlineCompletionOpenAiTemperature}")
            appendLine("Inline temperature (Codex CLI): ${settings.inlineCompletionCodexCliTemperature}")
            appendLine("Codex CLI path: ${settings.codexCliPath.ifBlank { "(empty)" }}")
            appendLine("Codex CLI extra args: ${settings.codexCliExtraArgs.ifBlank { "(empty)" }}")
            appendLine()

            if (editor == null) {
                appendLine("Editor: (none focused)")
                return@buildString
            }

            appendLine("Editor kind: ${editor.editorKind}")
            appendLine("Caret count: ${editor.caretModel.caretCount}")
            appendLine("InlineCompletion handler installed: ${InlineCompletion.getHandlerOrNull(editor) != null}")
            appendLine("TypedAction handler: ${TypedAction.getInstance().handler.javaClass.name}")
            appendLine("TypedAction raw handler: ${TypedAction.getInstance().rawHandler.javaClass.name}")
            appendLine()

            val caret = editor.caretModel.currentCaret
            val directCall = InlineCompletionEvent.DirectCall(editor, caret, e.dataContext)
            appendLine("Providers (DirectCall.isEnabled):")

            for (provider in InlineCompletionProvider.extensions()) {
                val providerId = runCatching { provider.id.toString() }.getOrDefault("<unknown>")
                val enabled = runCatching { provider.isEnabled(directCall) }
                    .fold(
                        onSuccess = { it.toString() },
                        onFailure = { "ERROR: ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                    )
                appendLine("- $providerId (${provider.javaClass.name}): $enabled")
            }
        }

        Messages.showInfoMessage(project, text, "Codex Inline Completion Diagnostics")
    }
}
