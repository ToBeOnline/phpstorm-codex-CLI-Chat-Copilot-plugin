package com.codex.phpstorm.actions

import com.codex.phpstorm.notifications.CodexNotifier
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

class CodexTriggerInlineCompletionAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            CodexNotifier.warn(project, "Focus an editor to trigger Codex inline completion.")
            return
        }

        val settings = CodexSettingsState.getInstance().state
        if (!settings.inlineCompletionEnabled) {
            CodexNotifier.warn(project, "Enable inline completions in Settings/Preferences | Tools | Codex.")
            return
        }

        if (editor.caretModel.caretCount != 1) {
            CodexNotifier.warn(project, "Inline completion requires a single caret.")
            return
        }

        val handler = InlineCompletion.getHandlerOrNull(editor)
        if (handler == null) {
            CodexNotifier.warn(project, "Inline completion is not available for this editor (kind=${editor.editorKind}).")
            return
        }

        handler.invoke(InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret, e.dataContext))
    }
}
