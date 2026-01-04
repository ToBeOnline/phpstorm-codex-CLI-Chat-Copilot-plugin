package com.codex.phpstorm.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.diagnostic.logger
import java.awt.BorderLayout
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI

class CodexCliToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panelResult = runCatching { CodexCliTerminalPanel(project) }
        val content = panelResult.fold(
            onSuccess = { panel ->
                contentFactory.createContent(panel.component, "", false).apply {
                    isCloseable = false
                    Disposer.register(this, panel)
                }
            },
            onFailure = { throwable ->
                LOG.warn("Failed to start Codex CLI terminal", throwable)
                val fallback = JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(8)
                    add(JBLabel("Could not start Codex CLI: ${throwable.message ?: "unknown error"}"), BorderLayout.CENTER)
                }
                contentFactory.createContent(fallback, "", false).apply { isCloseable = false }
            }
        )
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        private val LOG = logger<CodexCliToolWindowFactory>()
    }
}
