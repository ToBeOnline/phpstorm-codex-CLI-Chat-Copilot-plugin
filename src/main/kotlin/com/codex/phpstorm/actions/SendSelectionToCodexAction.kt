package com.codex.phpstorm.actions

import com.codex.phpstorm.notifications.CodexNotifier
import com.codex.phpstorm.session.CodexSessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

class SendSelectionToCodexAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selection = editor?.selectionModel?.selectedText?.trim()

        if (selection.isNullOrBlank()) {
            CodexNotifier.warn(project, "Select some code before asking Codex.")
            return
        }

        val prompt = "Review this selection:\n$selection"
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Codex Chat")
        if (toolWindow == null) {
            CodexNotifier.error(project, "Codex Chat tool window is unavailable.")
            return
        }

        toolWindow.show {
            project.getService(CodexSessionService::class.java).withPanel { panel ->
                panel.appendExternalPrompt(prompt)
            }
        }
    }
}
