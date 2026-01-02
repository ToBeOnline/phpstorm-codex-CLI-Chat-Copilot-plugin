package com.codex.phpstorm.toolwindow

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.IOException
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class CodexCliTerminalPanel(private val project: Project) : Disposable {

    val component: JComponent = buildUi()
    private var terminalWidget: TerminalWidget? = null
    private var terminalComponent: JComponent? = null

    private fun buildUi(): JComponent {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                add(JBLabel("Project base path is unavailable; cannot start Codex CLI terminal."), BorderLayout.CENTER)
            }
        }

        val settings = CodexSettingsState.getInstance().state
        val exe = settings.codexCliPath.ifBlank { "codex" }
        val args = mutableListOf<String>()
        args += exe
        val model = settings.codexCliModel.trim()
        if (model.isNotEmpty()) {
            args += listOf("-m", model)
        }

        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val runner = terminalManager.getTerminalRunner()
        val options = ShellStartupOptions.Builder()
            .workingDirectory(basePath)
            .shellCommand(args)
            .build()

        val widget = runner.startShellTerminalWidget(this, options, true)
        terminalWidget = widget
        terminalComponent = widget.component
        installEscapePassthrough()
        return widget.component
    }

    private fun installEscapePassthrough() {
        val widget = terminalWidget ?: return
        val root = terminalComponent ?: return

        IdeEventQueue.getInstance().addPreprocessor(IdeEventQueue.EventDispatcher { event: AWTEvent ->
            val keyEvent = event as? KeyEvent ?: return@EventDispatcher false
            val isEscapePressed = keyEvent.id == KeyEvent.KEY_PRESSED && keyEvent.keyCode == KeyEvent.VK_ESCAPE
            val isEscapeTyped = keyEvent.id == KeyEvent.KEY_TYPED && keyEvent.keyChar == '\u001B'
            if (!isEscapePressed && !isEscapeTyped) return@EventDispatcher false

            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return@EventDispatcher false
            if (!SwingUtilities.isDescendingFrom(focusOwner, root)) return@EventDispatcher false

            if (isEscapePressed) {
                val connector = widget.ttyConnector
                if (connector != null) {
                    try {
                        connector.write("\u001B")
                    } catch (_: IOException) {
                    }
                } else {
                    widget.ttyConnectorAccessor.executeWithTtyConnector { ttyConnector ->
                        try {
                            ttyConnector.write("\u001B")
                        } catch (_: IOException) {
                        }
                    }
                }
            }

            keyEvent.consume()
            false
        }, this)
    }

    override fun dispose() {
        // Terminal widget is disposed via parent disposable.
    }
}
