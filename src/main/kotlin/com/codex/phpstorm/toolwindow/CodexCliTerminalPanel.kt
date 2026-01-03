package com.codex.phpstorm.toolwindow

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.ComboBox
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.IOException
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class CodexCliTerminalPanel(private val project: Project) : Disposable {

    val component: JComponent = buildUi()
    private var terminalWidget: TerminalWidget? = null
    private var terminalComponent: JComponent? = null
    private val container = JPanel(BorderLayout())
    private val modelField = ComboBox<String>()

    private fun buildUi(): JComponent {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                add(JBLabel("Project base path is unavailable; cannot start Codex CLI terminal."), BorderLayout.CENTER)
            }
        }

        val settings = CodexSettingsState.getInstance().state
        val defaultModel = settings.codexCliTerminalModel.trim().ifEmpty { settings.codexCliModel.trim() }
        modelField.isEditable = true
        modelField.toolTipText = "Default model for Codex CLI terminal. Override per session here."
        modelField.selectedItem = defaultModel

        val restartButton = JButton("Restart with model")
        restartButton.addActionListener { restartTerminal(basePath) }

        val topBar = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(4, 8)
            add(JBLabel("Model"), BorderLayout.WEST)
            add(modelField, BorderLayout.CENTER)
            add(restartButton, BorderLayout.EAST)
        }

        container.border = JBUI.Borders.empty()
        container.add(topBar, BorderLayout.NORTH)
        restartTerminal(basePath)
        return container
    }

    private fun restartTerminal(basePath: String) {
        terminalComponent?.let { container.remove(it) }
        terminalWidget = null
        terminalComponent = null

        val settings = CodexSettingsState.getInstance().state
        val exe = settings.codexCliPath.ifBlank { "codex" }
        val args = mutableListOf<String>()
        args += exe
        val model = (modelField.editor.item as? String ?: modelField.selectedItem as? String).orEmpty().trim()
        if (model.isNotEmpty()) {
            args += listOf("-m", model)
        }
        val extraArgs = settings.codexCliExtraArgs.trim()
        if (extraArgs.isNotEmpty()) {
            args += extraArgs.split(" ").filter { it.isNotBlank() }
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
        container.add(widget.component, BorderLayout.CENTER)
        container.revalidate()
        container.repaint()
        installEscapePassthrough()
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
