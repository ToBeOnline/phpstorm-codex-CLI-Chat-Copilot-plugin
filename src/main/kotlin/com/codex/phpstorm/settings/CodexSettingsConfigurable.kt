package com.codex.phpstorm.settings

import com.codex.phpstorm.client.OpenAiModelsClient
import com.codex.phpstorm.client.OpenAiModelInfo
import com.codex.phpstorm.notifications.CodexNotifier
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.Locale
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JPasswordField

class CodexSettingsConfigurable : Configurable {

    private val state: CodexSettingsState = CodexSettingsState.getInstance()

    private val apiUrlField = JBTextField()
    private val apiKeyField = JPasswordField()
    private val openAiModelComboBox = ComboBox<String>()
    private val codexCliModelComboBox = ComboBox<String>()
    private val openAiInlineModelComboBox = ComboBox<String>()
    private val codexCliInlineModelComboBox = ComboBox<String>()
    private val openAiInlineTemperatureSlider = JSlider(0, 100, 10)
    private val codexCliInlineTemperatureSlider = JSlider(0, 100, 10)
    private val openAiInlineTemperatureValueLabel = JBLabel()
    private val codexCliInlineTemperatureValueLabel = JBLabel()
    private val refreshModelsButton = JButton("Refresh models")
    private val modelCardPanel = JPanel(CardLayout())
    private val inlineModelCardPanel = JPanel(CardLayout())
    private val inlineTemperatureCardPanel = JPanel(CardLayout())
    private val openAiInlineTipLabel = JBLabel()
    private val codexCliInlineTipLabel = JBLabel()
    private val inlineTipCardPanel = JPanel(CardLayout())
    private val temperatureSlider = JSlider(0, 100, 20)
    private val temperatureValueLabel = JBLabel()
    private val systemPromptArea = JBTextArea(3, 60).apply {
        toolTipText = """
            System prompt defines the assistant's role and style.
            Tips:
            - Be explicit about tone and format (e.g., concise code, no markdown).
            - Set language/framework context (e.g., “use PHP/WordPress”).
            - Keep temperature low (0.1–0.2) for predictable answers.
            - Inline completions also use this prompt; prefer concise, code-only guidance.
        """.trimIndent()
    }
    private val backendComboBox = ComboBox(CodexBackend.entries.toTypedArray())
    private val codexCliPathField = JBTextField()
    private val codexCliExtraArgsField = JBTextField()
    private val agentModeEnabled = JBCheckBox("Enable agent mode (allow tool calls)")
    private val allowFileRead = JBCheckBox("Allow reading project files", true)
    private val allowFileWrite = JBCheckBox("Allow writing project files", true)
    private val allowCommandExecution = JBCheckBox("Allow running local commands (dangerous)", false)
    private val inlineCompletionEnabled = JBCheckBox("Enable Copilot-style inline completions (ghost text)", false)
    private val inlineSuffixCharsField = JBTextField()
    private val notifyNewModels = JBCheckBox("Notify when new OpenAI models are available", true)
    private val notifyPluginUpdates = JBCheckBox("Notify when this plugin is updated (What's new)", true)
    private lateinit var component: JPanel
    private var fetchedOpenAiModels: List<OpenAiModelInfo> = emptyList()
    private var openAiChatModelIds: List<String> = emptyList()
    private var codexCliModelIds: List<String> = emptyList()

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        systemPromptArea.lineWrap = true
        systemPromptArea.wrapStyleWord = true
        openAiModelComboBox.isEditable = true
        codexCliModelComboBox.isEditable = true
        openAiInlineModelComboBox.isEditable = true
        codexCliInlineModelComboBox.isEditable = true

        val openAiModelPanel = JPanel(BorderLayout()).apply { add(openAiModelComboBox, BorderLayout.CENTER) }
        val codexCliModelPanel = JPanel(BorderLayout()).apply { add(codexCliModelComboBox, BorderLayout.CENTER) }
        modelCardPanel.add(openAiModelPanel, CodexBackend.OPENAI_API.name)
        modelCardPanel.add(codexCliModelPanel, CodexBackend.CODEX_CLI.name)
        val modelRow = JPanel(BorderLayout(8, 0)).apply {
            add(modelCardPanel, BorderLayout.CENTER)
            add(refreshModelsButton, BorderLayout.EAST)
        }

        val openAiInlineModelPanel = JPanel(BorderLayout()).apply { add(openAiInlineModelComboBox, BorderLayout.CENTER) }
        val codexCliInlineModelPanel = JPanel(BorderLayout()).apply { add(codexCliInlineModelComboBox, BorderLayout.CENTER) }
        inlineModelCardPanel.add(openAiInlineModelPanel, CodexBackend.OPENAI_API.name)
        inlineModelCardPanel.add(codexCliInlineModelPanel, CodexBackend.CODEX_CLI.name)

        val openAiInlineTemperaturePanel =
            JPanel(BorderLayout(8, 0)).apply {
                add(openAiInlineTemperatureSlider, BorderLayout.CENTER)
                add(openAiInlineTemperatureValueLabel, BorderLayout.EAST)
            }
        val codexCliInlineTemperaturePanel =
            JPanel(BorderLayout(8, 0)).apply {
                add(codexCliInlineTemperatureSlider, BorderLayout.CENTER)
                add(codexCliInlineTemperatureValueLabel, BorderLayout.EAST)
            }
        inlineTemperatureCardPanel.add(openAiInlineTemperaturePanel, CodexBackend.OPENAI_API.name)
        inlineTemperatureCardPanel.add(codexCliInlineTemperaturePanel, CodexBackend.CODEX_CLI.name)

        openAiInlineTipLabel.text = ""
        codexCliInlineTipLabel.text = ""
        inlineTipCardPanel.add(JPanel(BorderLayout()).apply { add(openAiInlineTipLabel, BorderLayout.CENTER) }, CodexBackend.OPENAI_API.name)
        inlineTipCardPanel.add(JPanel(BorderLayout()).apply { add(codexCliInlineTipLabel, BorderLayout.CENTER) }, CodexBackend.CODEX_CLI.name)

        val temperatureRow = JPanel(BorderLayout(8, 0)).apply {
            add(temperatureSlider, BorderLayout.CENTER)
            add(temperatureValueLabel, BorderLayout.EAST)
        }

        temperatureSlider.addChangeListener { temperatureValueLabel.text = formatTemperature(sliderToTemperature(temperatureSlider.value)) }
        openAiInlineTemperatureSlider.addChangeListener {
            openAiInlineTemperatureValueLabel.text = formatTemperature(sliderToTemperature(openAiInlineTemperatureSlider.value))
        }
        codexCliInlineTemperatureSlider.addChangeListener {
            codexCliInlineTemperatureValueLabel.text = formatTemperature(sliderToTemperature(codexCliInlineTemperatureSlider.value))
        }

        backendComboBox.addActionListener { updateBackendUi() }
        refreshModelsButton.addActionListener { refreshOpenAiModels() }

        component = FormBuilder.createFormBuilder()
            .addLabeledComponent("Backend", backendComboBox, 1, false)
            .addLabeledComponent("API base URL", apiUrlField, 1, false)
            .addLabeledComponent("API key", apiKeyField, 1, false)
            .addLabeledComponent("Model", modelRow, 1, false)
            .addLabeledComponent("Code completion model", inlineModelCardPanel, 1, false)
            .addLabeledComponent("Code completion temperature", inlineTemperatureCardPanel, 1, false)
            .addComponent(inlineTipCardPanel, 1)
            .addLabeledComponent("Inline suffix context (chars)", inlineSuffixCharsField, 1, false)
            .addLabeledComponent("Temperature", temperatureRow, 1, false)
            .addLabeledComponent("System prompt", systemPromptArea, 1, false)
            .addLabeledComponent("Codex CLI path", codexCliPathField, 1, false)
            .addLabeledComponent("Codex CLI extra args", codexCliExtraArgsField, 1, false)
            .addComponent(agentModeEnabled, 1)
            .addComponent(allowFileRead, 1)
            .addComponent(allowFileWrite, 1)
            .addComponent(allowCommandExecution, 1)
            .addComponent(inlineCompletionEnabled, 1)
            .addComponent(notifyNewModels, 1)
            .addComponent(notifyPluginUpdates, 1)
            .panel
        reset()
        maybeAutoRefreshModels()
        return component
    }

    override fun isModified(): Boolean {
        val s = state.state
        return apiUrlField.text != s.apiBaseUrl ||
            (backendComboBox.selectedItem as? CodexBackend)?.name != s.backend ||
            String(apiKeyField.password) != s.apiKey ||
            currentOpenAiModel() != s.model ||
            currentCodexCliModel() != s.codexCliModel ||
            sliderToTemperature(temperatureSlider.value) != s.temperature ||
            systemPromptArea.text != s.systemPrompt ||
            codexCliPathField.text != s.codexCliPath ||
            codexCliExtraArgsField.text != s.codexCliExtraArgs ||
            agentModeEnabled.isSelected != s.agentModeEnabled ||
            allowFileRead.isSelected != s.allowFileRead ||
            allowFileWrite.isSelected != s.allowFileWrite ||
            allowCommandExecution.isSelected != s.allowCommandExecution ||
            inlineCompletionEnabled.isSelected != s.inlineCompletionEnabled ||
            currentOpenAiInlineModel() != s.inlineCompletionOpenAiModel ||
            sliderToTemperature(openAiInlineTemperatureSlider.value) != s.inlineCompletionOpenAiTemperature ||
            currentCodexCliInlineModel() != s.inlineCompletionCodexCliModel ||
            sliderToTemperature(codexCliInlineTemperatureSlider.value) != s.inlineCompletionCodexCliTemperature ||
            sanitizeInlineSuffixChars(inlineSuffixCharsField.text, s.inlineCompletionSuffixChars) != s.inlineCompletionSuffixChars ||
            notifyNewModels.isSelected != s.notifyAboutNewModels ||
            notifyPluginUpdates.isSelected != s.notifyAboutPluginUpdates
    }

    override fun apply() {
        val openAiModel = currentOpenAiModel().ifEmpty { "gpt-4o-mini" }
        val backend = backendComboBox.selectedItem as? CodexBackend ?: CodexBackend.OPENAI_API
        val suffixChars = sanitizeInlineSuffixChars(inlineSuffixCharsField.text, state.state.inlineCompletionSuffixChars)
        state.loadState(
            CodexSettingsState.State(
                backend = backend.name,
                apiBaseUrl = apiUrlField.text.trim(),
                apiKey = String(apiKeyField.password),
                model = openAiModel,
                temperature = sliderToTemperature(temperatureSlider.value),
                systemPrompt = systemPromptArea.text.trim(),
                codexCliPath = codexCliPathField.text.trim().ifEmpty { "codex" },
                codexCliExtraArgs = codexCliExtraArgsField.text.trim(),
                codexCliTimeoutMs = state.state.codexCliTimeoutMs,
                codexCliModel = currentCodexCliModel(),
                agentModeEnabled = agentModeEnabled.isSelected,
                allowFileRead = allowFileRead.isSelected,
                allowFileWrite = allowFileWrite.isSelected,
                allowCommandExecution = allowCommandExecution.isSelected,
                inlineCompletionEnabled = inlineCompletionEnabled.isSelected,
                inlineCompletionOpenAiModel = currentOpenAiInlineModel(),
                inlineCompletionOpenAiTemperature = sliderToTemperature(openAiInlineTemperatureSlider.value),
                inlineCompletionCodexCliModel = currentCodexCliInlineModel(),
                inlineCompletionCodexCliTemperature = sliderToTemperature(codexCliInlineTemperatureSlider.value),
                inlineCompletionSuffixChars = suffixChars,
                notifyAboutNewModels = notifyNewModels.isSelected,
                notifyAboutPluginUpdates = notifyPluginUpdates.isSelected,
                knownOpenAiChatModelIds = state.state.knownOpenAiChatModelIds.toMutableList(),
                lastOpenAiModelCheckEpochMs = state.state.lastOpenAiModelCheckEpochMs,
                lastSeenPluginVersion = state.state.lastSeenPluginVersion
            )
        )
    }

    override fun reset() {
        val s = state.state
        apiUrlField.text = s.apiBaseUrl
        apiKeyField.text = s.apiKey
        temperatureSlider.value = temperatureToSlider(s.temperature)
        systemPromptArea.text = s.systemPrompt
        backendComboBox.selectedItem = runCatching { CodexBackend.valueOf(s.backend) }.getOrDefault(CodexBackend.OPENAI_API)
        codexCliPathField.text = s.codexCliPath
        codexCliExtraArgsField.text = s.codexCliExtraArgs
        openAiModelComboBox.selectedItem = s.model
        codexCliModelComboBox.selectedItem = s.codexCliModel
        openAiInlineModelComboBox.selectedItem = s.inlineCompletionOpenAiModel.ifBlank { INLINE_MODEL_DEFAULT_SENTINEL }
        codexCliInlineModelComboBox.selectedItem = s.inlineCompletionCodexCliModel.ifBlank { INLINE_MODEL_DEFAULT_SENTINEL }
        openAiInlineTemperatureSlider.value = temperatureToSlider(s.inlineCompletionOpenAiTemperature)
        codexCliInlineTemperatureSlider.value = temperatureToSlider(s.inlineCompletionCodexCliTemperature)
        inlineSuffixCharsField.text = s.inlineCompletionSuffixChars.toString()
        updateModelLists()
        updateBackendUi()
        updateTemperatureLabels()
        agentModeEnabled.isSelected = s.agentModeEnabled
        allowFileRead.isSelected = s.allowFileRead
        allowFileWrite.isSelected = s.allowFileWrite
        allowCommandExecution.isSelected = s.allowCommandExecution
        inlineCompletionEnabled.isSelected = s.inlineCompletionEnabled
        notifyNewModels.isSelected = s.notifyAboutNewModels
        notifyPluginUpdates.isSelected = s.notifyAboutPluginUpdates
    }

    override fun disposeUIResources() {
        // no-op
    }

    private fun sliderToTemperature(value: Int): Double = (value.coerceIn(0, 100)) / 100.0
    private fun temperatureToSlider(value: Double): Int = (value.coerceIn(0.0, 1.0) * 100).toInt()

    private fun formatTemperature(value: Double): String = String.format(Locale.US, "%.2f", value.coerceIn(0.0, 1.0))

    private fun updateTemperatureLabels() {
        temperatureValueLabel.text = formatTemperature(sliderToTemperature(temperatureSlider.value))
        openAiInlineTemperatureValueLabel.text = formatTemperature(sliderToTemperature(openAiInlineTemperatureSlider.value))
        codexCliInlineTemperatureValueLabel.text = formatTemperature(sliderToTemperature(codexCliInlineTemperatureSlider.value))
    }

    private fun sanitizeInlineSuffixChars(text: String, fallback: Int): Int {
        val parsed = text.toIntOrNull() ?: return fallback
        return parsed.coerceIn(200, 8000)
    }

    private fun updateBackendUi() {
        val backend = backendComboBox.selectedItem as? CodexBackend ?: CodexBackend.OPENAI_API
        (modelCardPanel.layout as CardLayout).show(modelCardPanel, backend.name)
        (inlineModelCardPanel.layout as CardLayout).show(inlineModelCardPanel, backend.name)
        (inlineTemperatureCardPanel.layout as CardLayout).show(inlineTemperatureCardPanel, backend.name)
        (inlineTipCardPanel.layout as CardLayout).show(inlineTipCardPanel, backend.name)
        if (::component.isInitialized) {
            component.revalidate()
            component.repaint()
        }
    }

    private fun refreshOpenAiModels() {
        val apiBaseUrl = apiUrlField.text.trim()
        val apiKey = String(apiKeyField.password)
        if (apiBaseUrl.isBlank()) {
            CodexNotifier.error(null, "API base URL is required to fetch models.")
            return
        }
        if (apiKey.isBlank()) {
            CodexNotifier.error(null, "API key is required to fetch models.")
            return
        }

        refreshModelsButton.isEnabled = false
        refreshModelsButton.text = "Loading..."
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Codex: Fetch OpenAI models", false) {
            private var result: Result<List<OpenAiModelInfo>> = Result.success(emptyList())
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                result = OpenAiModelsClient.listModels(apiBaseUrl, apiKey)
            }

            override fun onFinished() {
                refreshModelsButton.isEnabled = true
                refreshModelsButton.text = "Refresh models"
                result.fold(
                    onSuccess = { models ->
                        fetchedOpenAiModels = models
                        updateModelLists()
                        val settings = CodexSettingsState.getInstance().state
                        val now = System.currentTimeMillis()
                        val chatIds = OpenAiModelsClient
                            .defaultChatModelIds(models.map { it.id })
                            .distinct()
                        val known = settings.knownOpenAiChatModelIds.toSet()
                        val newModels = if (known.isEmpty()) emptyList() else chatIds.filter { it !in known }
                        settings.knownOpenAiChatModelIds = chatIds.toMutableList()
                        settings.lastOpenAiModelCheckEpochMs = now

                        if (newModels.isNotEmpty() && settings.notifyAboutNewModels) {
                            val preview = newModels.take(6)
                            val suffix = if (newModels.size > preview.size) " (+${newModels.size - preview.size} more)" else ""
                            CodexNotifier.info(null, "New OpenAI models available: ${preview.joinToString(", ")}$suffix")
                        } else {
                            CodexNotifier.info(null, "Loaded ${models.size} models from OpenAI.")
                        }
                    },
                    onFailure = { error ->
                        CodexNotifier.error(null, error.message ?: "Failed to fetch models.")
                    }
                )
            }
        })
    }

    private fun maybeAutoRefreshModels() {
        val apiBaseUrl = apiUrlField.text.trim()
        val apiKey = String(apiKeyField.password)
        if (apiBaseUrl.isBlank() || apiKey.isBlank()) return
        if (fetchedOpenAiModels.isNotEmpty()) return
        refreshOpenAiModels()
    }

    private fun currentOpenAiModel(): String =
        (openAiModelComboBox.editor.item as? String ?: openAiModelComboBox.selectedItem as? String).orEmpty().trim()

    private fun currentCodexCliModel(): String =
        (codexCliModelComboBox.editor.item as? String ?: codexCliModelComboBox.selectedItem as? String).orEmpty().trim()

    private fun currentOpenAiInlineModelRaw(): String =
        (openAiInlineModelComboBox.editor.item as? String ?: openAiInlineModelComboBox.selectedItem as? String).orEmpty().trim()

    private fun currentCodexCliInlineModelRaw(): String =
        (codexCliInlineModelComboBox.editor.item as? String ?: codexCliInlineModelComboBox.selectedItem as? String).orEmpty().trim()

    private fun currentOpenAiInlineModel(): String {
        val raw = currentOpenAiInlineModelRaw()
        return if (raw.isBlank() || raw == INLINE_MODEL_DEFAULT_SENTINEL) "" else raw
    }

    private fun currentCodexCliInlineModel(): String {
        val raw = currentCodexCliInlineModelRaw()
        return if (raw.isBlank() || raw == INLINE_MODEL_DEFAULT_SENTINEL) "" else raw
    }

    private fun updateModelLists() {
        val openAiFallbackNewestFirst = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini")
        val cliPinnedNewestFirst = listOf("gpt-5.2-codex", "gpt-5.1-codex", "gpt-5.1-codex-max", "o3", "o3-mini")

        val openAiFromApiNewestFirst = OpenAiModelsClient.defaultChatModelIds(fetchedOpenAiModels.map { it.id })
        val openAiModels = distinctPreserveOrder(openAiFromApiNewestFirst + openAiFallbackNewestFirst)
        val cliModels = distinctPreserveOrder(cliPinnedNewestFirst + openAiFromApiNewestFirst + openAiFallbackNewestFirst)

        openAiChatModelIds = openAiModels
        codexCliModelIds = cliModels

        val openAiSelected = currentOpenAiModel()
        openAiModelComboBox.model = DefaultComboBoxModel(openAiModels.toTypedArray())
        if (openAiSelected.isNotEmpty()) openAiModelComboBox.selectedItem = openAiSelected

        val cliSelected = currentCodexCliModel()
        codexCliModelComboBox.model = DefaultComboBoxModel(cliModels.toTypedArray())
        if (cliSelected.isNotEmpty()) codexCliModelComboBox.selectedItem = cliSelected

        val openAiInlineSelected = currentOpenAiInlineModelRaw()
        openAiInlineModelComboBox.model =
            DefaultComboBoxModel((listOf(INLINE_MODEL_DEFAULT_SENTINEL) + openAiModels).toTypedArray())
        openAiInlineModelComboBox.selectedItem =
            if (openAiInlineSelected.isBlank()) INLINE_MODEL_DEFAULT_SENTINEL else openAiInlineSelected

        val cliInlineSelected = currentCodexCliInlineModelRaw()
        codexCliInlineModelComboBox.model =
            DefaultComboBoxModel((listOf(INLINE_MODEL_DEFAULT_SENTINEL) + cliModels).toTypedArray())
        codexCliInlineModelComboBox.selectedItem =
            if (cliInlineSelected.isBlank()) INLINE_MODEL_DEFAULT_SENTINEL else cliInlineSelected

        updateInlineCompletionTips()
    }

    private fun updateInlineCompletionTips() {
        openAiInlineTipLabel.text = buildInlineTipText(
            backend = CodexBackend.OPENAI_API,
            modelIds = openAiChatModelIds
        )
        codexCliInlineTipLabel.text = buildInlineTipText(
            backend = CodexBackend.CODEX_CLI,
            modelIds = codexCliModelIds
        )
    }

    private fun buildInlineTipText(backend: CodexBackend, modelIds: List<String>): String {
        val recommendedModel = recommendedInlineModelId(backend, modelIds)
        val recommendedTemp = "0.0–0.2"
        return if (recommendedModel == null) {
            "Tip: click “Refresh models” to load the latest models."
        } else {
            when (backend) {
                CodexBackend.OPENAI_API ->
                    "Tip: For code completion, use $recommendedModel and set temperature to $recommendedTemp."
                CodexBackend.CODEX_CLI ->
                    "Tip: For code completion, use $recommendedModel (Codex CLI) and set temperature to $recommendedTemp."
            }
        }
    }

    private fun recommendedInlineModelId(backend: CodexBackend, modelIds: List<String>): String? {
        val preferred = when (backend) {
            CodexBackend.OPENAI_API -> listOf("gpt-4.1-mini", "gpt-4o-mini", "gpt-4.1", "gpt-4o")
            CodexBackend.CODEX_CLI -> listOf("gpt-5.2-codex", "gpt-5.1-codex", "o3-mini", "o3", "gpt-4.1-mini")
        }
        val set = modelIds.toHashSet()
        return preferred.firstOrNull { set.contains(it) } ?: modelIds.firstOrNull()
    }

    private fun distinctPreserveOrder(items: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (item in items) {
            if (item.isBlank()) continue
            seen.add(item)
        }
        return seen.toList()
    }

    companion object {
        private const val INLINE_MODEL_DEFAULT_SENTINEL = "(Same as Model)"
    }
}
