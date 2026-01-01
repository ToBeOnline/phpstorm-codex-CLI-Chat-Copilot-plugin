package com.codex.phpstorm.agent

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CodexApprovalDialog(
    project: Project,
    private val titleText: String,
    private val detailsText: String,
    private val checkboxText: String = "Auto-approve further actions (this session)"
) : DialogWrapper(project, true) {

    private val detailsArea = JBTextArea()
    private val approveAllCheckbox = JBCheckBox(checkboxText, false)

    init {
        title = "Codex Approval"
        setOKButtonText("Approve")
        setCancelButtonText("Deny")
        init()
    }

    val approveAllForSession: Boolean
        get() = approveAllCheckbox.isSelected

    override fun createCenterPanel(): JComponent {
        detailsArea.text = buildString {
            append(titleText.trim())
            append("\n\n")
            append(detailsText.trim())
        }
        detailsArea.isEditable = false
        detailsArea.lineWrap = true
        detailsArea.wrapStyleWord = true
        detailsArea.border = JBUI.Borders.empty(8)

        val scroll = JBScrollPane(detailsArea).apply {
            border = JBUI.Borders.empty()
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(scroll, BorderLayout.CENTER)
            add(approveAllCheckbox, BorderLayout.SOUTH)
            preferredSize = JBUI.size(640, 360)
        }
    }
}

