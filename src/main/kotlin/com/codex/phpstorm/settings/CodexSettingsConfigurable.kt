package com.codex.phpstorm.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JPasswordField

class CodexSettingsConfigurable : Configurable {

    private val state: CodexSettingsState = CodexSettingsState.getInstance()

    private val apiUrlField = JBTextField()
    private val apiKeyField = JPasswordField()
    private val modelField = JBTextField()
    private val temperatureSlider = JSlider(0, 100, 20)
    private val systemPromptArea = JBTextArea(3, 60)
    private val backendComboBox = ComboBox(CodexBackend.entries.toTypedArray())
    private val codexCliPathField = JBTextField()
    private val codexCliExtraArgsField = JBTextField()
    private val agentModeEnabled = JBCheckBox("Enable agent mode (allow tool calls)")
    private val allowFileRead = JBCheckBox("Allow reading project files", true)
    private val allowFileWrite = JBCheckBox("Allow writing project files", true)
    private val allowCommandExecution = JBCheckBox("Allow running local commands (dangerous)", false)
    private lateinit var component: JPanel

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        systemPromptArea.lineWrap = true
        systemPromptArea.wrapStyleWord = true

        component = FormBuilder.createFormBuilder()
            .addLabeledComponent("Backend", backendComboBox, 1, false)
            .addLabeledComponent("API base URL", apiUrlField, 1, false)
            .addLabeledComponent("API key", apiKeyField, 1, false)
            .addLabeledComponent("Model", modelField, 1, false)
            .addLabeledComponent("Temperature", temperatureSlider, 1, false)
            .addLabeledComponent("System prompt", systemPromptArea, 1, false)
            .addLabeledComponent("Codex CLI path", codexCliPathField, 1, false)
            .addLabeledComponent("Codex CLI extra args", codexCliExtraArgsField, 1, false)
            .addComponent(agentModeEnabled, 1)
            .addComponent(allowFileRead, 1)
            .addComponent(allowFileWrite, 1)
            .addComponent(allowCommandExecution, 1)
            .panel
        reset()
        return component
    }

    override fun isModified(): Boolean {
        val s = state.state
        return apiUrlField.text != s.apiBaseUrl ||
            (backendComboBox.selectedItem as? CodexBackend)?.name != s.backend ||
            String(apiKeyField.password) != s.apiKey ||
            modelField.text != s.model ||
            sliderToTemperature(temperatureSlider.value) != s.temperature ||
            systemPromptArea.text != s.systemPrompt ||
            codexCliPathField.text != s.codexCliPath ||
            codexCliExtraArgsField.text != s.codexCliExtraArgs ||
            agentModeEnabled.isSelected != s.agentModeEnabled ||
            allowFileRead.isSelected != s.allowFileRead ||
            allowFileWrite.isSelected != s.allowFileWrite ||
            allowCommandExecution.isSelected != s.allowCommandExecution
    }

    override fun apply() {
        state.loadState(
            CodexSettingsState.State(
                backend = (backendComboBox.selectedItem as? CodexBackend ?: CodexBackend.OPENAI_API).name,
                apiBaseUrl = apiUrlField.text.trim(),
                apiKey = String(apiKeyField.password),
                model = modelField.text.trim().ifEmpty { "codex-chat" },
                temperature = sliderToTemperature(temperatureSlider.value),
                systemPrompt = systemPromptArea.text.trim(),
                codexCliPath = codexCliPathField.text.trim().ifEmpty { "codex" },
                codexCliExtraArgs = codexCliExtraArgsField.text.trim(),
                codexCliTimeoutMs = state.state.codexCliTimeoutMs,
                agentModeEnabled = agentModeEnabled.isSelected,
                allowFileRead = allowFileRead.isSelected,
                allowFileWrite = allowFileWrite.isSelected,
                allowCommandExecution = allowCommandExecution.isSelected
            )
        )
    }

    override fun reset() {
        val s = state.state
        apiUrlField.text = s.apiBaseUrl
        apiKeyField.text = s.apiKey
        modelField.text = s.model
        temperatureSlider.value = temperatureToSlider(s.temperature)
        systemPromptArea.text = s.systemPrompt
        backendComboBox.selectedItem = runCatching { CodexBackend.valueOf(s.backend) }.getOrDefault(CodexBackend.OPENAI_API)
        codexCliPathField.text = s.codexCliPath
        codexCliExtraArgsField.text = s.codexCliExtraArgs
        agentModeEnabled.isSelected = s.agentModeEnabled
        allowFileRead.isSelected = s.allowFileRead
        allowFileWrite.isSelected = s.allowFileWrite
        allowCommandExecution.isSelected = s.allowCommandExecution
    }

    override fun disposeUIResources() {
        // no-op
    }

    private fun sliderToTemperature(value: Int): Double = (value.coerceIn(0, 100)) / 100.0
    private fun temperatureToSlider(value: Double): Int = (value.coerceIn(0.0, 1.0) * 100).toInt()
}
