package com.codex.phpstorm.inline

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler

class CodexInlineCompletionTypedHandler(
    private val delegate: TypedActionHandler
) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        delegate.execute(editor, charTyped, dataContext)

        val settings = CodexSettingsState.getInstance().state
        if (!settings.inlineCompletionEnabled) return

        if (InlineCompletion.getHandlerOrNull(editor) == null) return
        val app = ApplicationManager.getApplication()
        val context = dataContext
        app.invokeLater {
            if (editor.isDisposed) return@invokeLater
            if (!CodexSettingsState.getInstance().state.inlineCompletionEnabled) return@invokeLater
            val currentHandler = InlineCompletion.getHandlerOrNull(editor) ?: return@invokeLater
            val caret = editor.caretModel.currentCaret
            currentHandler.invoke(InlineCompletionEvent.DirectCall(editor, caret, context))
        }
    }
}
