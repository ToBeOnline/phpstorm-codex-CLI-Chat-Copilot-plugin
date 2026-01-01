package com.codex.phpstorm.session

import com.codex.phpstorm.toolwindow.CodexChatPanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.lang.ref.WeakReference

@Service(Service.Level.PROJECT)
class CodexSessionService(private val project: Project) {

    private var panelRef: WeakReference<CodexChatPanel>? = null

    fun attach(panel: CodexChatPanel) {
        panelRef = WeakReference(panel)
    }

    fun detach(panel: CodexChatPanel) {
        val current = panelRef?.get()
        if (current == panel) {
            panelRef = null
        }
    }

    fun withPanel(block: (CodexChatPanel) -> Unit) {
        panelRef?.get()?.let(block)
    }
}
