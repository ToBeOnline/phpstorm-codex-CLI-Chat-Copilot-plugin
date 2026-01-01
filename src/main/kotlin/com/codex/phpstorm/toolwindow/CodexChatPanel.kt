package com.codex.phpstorm.toolwindow

import com.codex.phpstorm.agent.CodexApprovalDialog
import com.codex.phpstorm.agent.CodexToolCatalog
import com.codex.phpstorm.agent.CodexToolExecutor
import com.codex.phpstorm.client.ChatCompletionMessage
import com.codex.phpstorm.client.CodexCliClient
import com.codex.phpstorm.client.CodexClient
import com.codex.phpstorm.client.ToolCall
import com.codex.phpstorm.client.ToolDefinition
import com.codex.phpstorm.notifications.CodexNotifier
import com.codex.phpstorm.session.CodexSessionService
import com.codex.phpstorm.settings.CodexBackend
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class CodexChatPanel(private val project: Project) {

    private val transcript = JTextPane()
    private val inputArea = JBTextArea(4, 80)
    private val selectionToggle = JBCheckBox("Include editor selection", true)
    private val autoApproveToggle = JBCheckBox("Auto-approve actions (this session)", false)
    private val systemPromptField = JBTextField()
    private val sendButton = JButton("Send")
    private val clearButton = JButton("Clear")
    private val conversation = mutableListOf<ChatCompletionMessage>()
    @Volatile private var autoApproveActions: Boolean = false
    val component: JComponent = buildUi()

    private val agentInstructions = """
        You may use the provided tools to inspect and modify the local project.
        - Prefer reading files (read_file) before making changes.
        - When editing, use write_file with the full updated file content.
        - If you need to run tests or git commands, use run_command.
        - Be explicit and safe: only change what the user asked for.
    """.trimIndent().trim()

    init {
        project.getService(CodexSessionService::class.java).attach(this)
        val settings = CodexSettingsState.getInstance().state
        systemPromptField.text = settings.systemPrompt
    }

    fun appendExternalPrompt(prompt: String) {
        inputArea.text = prompt.trim()
        inputArea.caretPosition = inputArea.text.length
        ToolWindowManager.getInstance(project).getToolWindow("Codex Chat")?.show()
    }

    private fun buildUi(): JComponent {
        transcript.isEditable = false
        transcript.margin = JBInsets(8, 8, 8, 8)
        val transcriptScroll = JBScrollPane(transcript)

        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.emptyText.text = "Ask Codex anything about your project"
        inputArea.inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "send")
        inputArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                sendFromUi()
            }
        })

        sendButton.addActionListener { sendFromUi() }
        clearButton.addActionListener { clearConversation() }
        autoApproveToggle.addActionListener { autoApproveActions = autoApproveToggle.isSelected }

        val controls = JPanel(VerticalLayout(4)).apply {
            border = JBUI.Borders.empty(8)
            add(JLabel("System prompt"))
            add(systemPromptField)
            add(selectionToggle)
            add(autoApproveToggle)
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyTop(8)
                add(sendButton, BorderLayout.WEST)
                add(clearButton, BorderLayout.EAST)
            })
        }

        val inputScroll = JBScrollPane(inputArea)
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(inputScroll, BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            add(JBSplitter(true, 0.65f).apply {
                firstComponent = transcriptScroll
                secondComponent = bottomPanel
            }, BorderLayout.CENTER)
        }
    }

    private fun ensureSystemPrompt() {
        val promptText = systemPromptField.text.trim()
        if (promptText.isEmpty()) {
            return
        }
        CodexSettingsState.getInstance().state.systemPrompt = promptText
        if (conversation.isNotEmpty() && conversation.first().role == "system") {
            if (conversation.first().content != promptText) {
                conversation[0] = ChatCompletionMessage(role = "system", content = promptText)
            }
        } else {
            conversation.add(0, ChatCompletionMessage(role = "system", content = promptText))
        }
    }

    private fun ensureAgentInstructions(enabled: Boolean) {
        val existingIndex = conversation.indexOfFirst { it.role == "system" && it.content == agentInstructions }
        if (!enabled) {
            if (existingIndex >= 0) {
                conversation.removeAt(existingIndex)
            }
            return
        }
        if (existingIndex >= 0) return

        val insertIndex = if (conversation.isNotEmpty() && conversation.first().role == "system") 1 else 0
        conversation.add(insertIndex, ChatCompletionMessage(role = "system", content = agentInstructions))
    }

    private fun sendFromUi() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) {
            return
        }

        val selection = if (selectionToggle.isSelected) captureSelection() else null
        val finalPrompt = buildString {
            append(text)
            if (!selection.isNullOrBlank()) {
                append("\n\nContext:\n```\n")
                append(selection)
                append("\n```")
            }
        }

        inputArea.text = ""
        appendMessage("You", finalPrompt)
        ensureSystemPrompt()
        val settings = CodexSettingsState.getInstance().state
        val backend = runCatching { CodexBackend.valueOf(settings.backend) }.getOrDefault(CodexBackend.OPENAI_API)
        val toolsPreview = if (backend == CodexBackend.OPENAI_API) CodexToolCatalog.toolsFor(settings) else emptyList()
        ensureAgentInstructions(backend == CodexBackend.OPENAI_API && toolsPreview.isNotEmpty())
        conversation.add(ChatCompletionMessage(role = "user", content = finalPrompt))
        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Codex Chat", false) {
            private var assistantReply: String? = null
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val settings = CodexSettingsState.getInstance().state
                val backend = runCatching { CodexBackend.valueOf(settings.backend) }.getOrDefault(CodexBackend.OPENAI_API)
                assistantReply = when (backend) {
                    CodexBackend.CODEX_CLI -> {
                        val result = CodexCliClient.getInstance(project).chat(conversation.toList())
                        val reply = result.getOrElse { throw it }.text
                        conversation.add(ChatCompletionMessage(role = "assistant", content = reply))
                        reply
                    }
                    CodexBackend.OPENAI_API -> {
                        val tools = CodexToolCatalog.toolsFor(settings)
                        if (tools.isEmpty()) {
                            val assistant = CodexClient.getInstance(project).createChatCompletion(conversation.toList(), null)
                                .getOrElse { throw it }
                            conversation.add(assistant)
                            assistant.content
                        } else {
                            runAgentLoop(tools, indicator)
                        }
                    }
                }
            }

            override fun onSuccess() {
                assistantReply?.let {
                    appendMessage("Codex", it)
                }
                resetSendButton()
            }

            override fun onThrowable(error: Throwable) {
                CodexNotifier.error(project, error.message ?: "Codex request failed")
                resetSendButton()
            }
        })
    }

    private fun runAgentLoop(tools: List<ToolDefinition>, indicator: ProgressIndicator): String {
        val client = CodexClient.getInstance(project)
        val executor = CodexToolExecutor(project)

        var iterations = 0
        while (iterations < 12 && !indicator.isCanceled) {
            val assistant = client.createChatCompletion(conversation.toList(), tools).getOrElse { throw it }
            conversation.add(assistant)

            val toolCalls = assistant.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                return assistant.content ?: ""
            }

            for (toolCall in toolCalls) {
                val approved = requestApproval(toolCall)
                if (!approved) {
                    conversation.add(
                        ChatCompletionMessage(
                            role = "tool",
                            toolCallId = toolCall.id,
                            content = """{"error":"Denied by user"}"""
                        )
                    )
                    appendMessageLater("Codex", "Denied: ${executor.describe(toolCall)}")
                    continue
                }

                val execution = executor.execute(toolCall)
                conversation.add(
                    ChatCompletionMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = execution.toolResponseContent
                    )
                )
                appendMessageLater("Codex", execution.userSummary)
            }

            iterations++
        }

        if (indicator.isCanceled) {
            return "Canceled."
        }
        return "Stopped after too many tool calls."
    }

    private fun requestApproval(toolCall: ToolCall): Boolean {
        if (autoApproveActions) return true

        var approved = false
        ApplicationManager.getApplication().invokeAndWait {
            val dialog = CodexApprovalDialog(
                project = project,
                titleText = "Codex wants to run: ${toolCall.function.name}",
                detailsText = toolCall.function.arguments
            )
            approved = dialog.showAndGet()
            if (approved && dialog.approveAllForSession) {
                autoApproveActions = true
                autoApproveToggle.isSelected = true
            }
        }
        if (!approved) {
            return false
        }
        return true
    }

    private fun appendMessageLater(author: String, content: String) {
        ApplicationManager.getApplication().invokeLater {
            appendMessage(author, content)
        }
    }

    private fun resetSendButton() {
        sendButton.isEnabled = true
        sendButton.text = "Send"
    }

    private fun clearConversation() {
        conversation.clear()
        transcript.text = ""
    }

    private fun appendMessage(author: String, content: String) {
        val doc = transcript.styledDocument
        val labelColor = UIUtil.getLabelForeground()
        val authorAttr = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, labelColor)
        }
        val bodyAttr = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, labelColor)
        }
        try {
            if (doc.length > 0) {
                doc.insertString(doc.length, "\n\n", null)
            }
            doc.insertString(doc.length, "$author\n", authorAttr)
            doc.insertString(doc.length, content.trim(), bodyAttr)
        } catch (e: BadLocationException) {
            // ignore, this only affects rendering
        }
        transcript.caretPosition = doc.length
    }

    private fun captureSelection(): String? {
        val editor = currentEditor() ?: return null
        val selectionModel: SelectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return null
        return selectionModel.selectedText
    }

    private fun currentEditor() = EditorFactory.getInstance().allEditors
        .firstOrNull { it.project == project && it.selectionModel.hasSelection() }
        ?: EditorFactory.getInstance().allEditors.firstOrNull { it.project == project }
}
