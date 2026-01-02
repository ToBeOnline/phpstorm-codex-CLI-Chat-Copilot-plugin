package com.codex.phpstorm.inline

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.util.Computable

class CodexInlineCompletionRawTypedHandler(
    private val delegate: TypedActionHandler
) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        delegate.execute(editor, charTyped, dataContext)

        if (!CodexSettingsState.getInstance().state.inlineCompletionEnabled) return

        val app = ApplicationManager.getApplication()
        app.invokeLater {
            if (!CodexSettingsState.getInstance().state.inlineCompletionEnabled) return@invokeLater

            val handler = InlineCompletion.getHandlerOrNull(editor) ?: return@invokeLater
            val caret = app.runReadAction(Computable<Caret?> { editor.caretModel.currentCaret }) ?: return@invokeLater
            handler.invoke(InlineCompletionEvent.DirectCall(editor, caret, dataContext))
        }
    }
}

