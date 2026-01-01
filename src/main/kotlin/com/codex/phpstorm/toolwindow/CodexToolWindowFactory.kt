package com.codex.phpstorm.toolwindow

import com.codex.phpstorm.session.CodexSessionService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CodexToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodexChatPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel.component, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

        project.getService(CodexSessionService::class.java).attach(panel)
    }
}
