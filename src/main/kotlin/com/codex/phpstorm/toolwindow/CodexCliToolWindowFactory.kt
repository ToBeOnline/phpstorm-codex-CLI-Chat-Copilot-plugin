package com.codex.phpstorm.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.diagnostic.logger
import java.awt.BorderLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
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

        ensureAboveTerminal(project, toolWindow.id)
    }

    private fun ensureAboveTerminal(project: Project, codexToolWindowId: String) {
        UIUtil.invokeLaterIfNeeded {
            val manager = ToolWindowManagerEx.getInstanceEx(project)
            val currentLayout = manager.getLayout()
            val codexInfo = currentLayout.getInfo(codexToolWindowId) as? WindowInfoImpl ?: return@invokeLaterIfNeeded
            if (codexInfo.isFromPersistentSettings) return@invokeLaterIfNeeded

            val terminalInfo = currentLayout.getInfo("Terminal") as? WindowInfoImpl ?: return@invokeLaterIfNeeded
            if (terminalInfo.anchor != ToolWindowAnchor.BOTTOM) return@invokeLaterIfNeeded

            val updated = currentLayout.copy()
            val updatedCodexInfo = updated.getInfo(codexToolWindowId) as? WindowInfoImpl ?: return@invokeLaterIfNeeded
            val desiredPaneId = terminalInfo.safeToolWindowPaneId
            val desiredOrder = terminalInfo.order
            updated.setAnchor(updatedCodexInfo, desiredPaneId, ToolWindowAnchor.BOTTOM, desiredOrder)
            manager.setLayout(updated)
        }
    }

    companion object {
        private val LOG = logger<CodexCliToolWindowFactory>()
    }
}
