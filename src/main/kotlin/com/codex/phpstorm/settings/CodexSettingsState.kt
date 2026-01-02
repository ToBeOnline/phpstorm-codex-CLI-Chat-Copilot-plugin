package com.codex.phpstorm.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "CodexSettingsState", storages = [Storage("codex_settings.xml")])
class CodexSettingsState : PersistentStateComponent<CodexSettingsState.State> {

    data class State(
        var backend: String = CodexBackend.OPENAI_API.name,
        var apiBaseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-4o-mini",
        var temperature: Double = 0.2,
        var systemPrompt: String = "You are Codex, a coding assistant integrated inside PhpStorm.",
        var codexCliPath: String = "codex",
        var codexCliExtraArgs: String = "",
        var codexCliModel: String = "",
        var codexCliTimeoutMs: Int = 600000,
        var agentModeEnabled: Boolean = false,
        var allowFileRead: Boolean = true,
        var allowFileWrite: Boolean = true,
        var allowCommandExecution: Boolean = false,
        var inlineCompletionEnabled: Boolean = false,
        var inlineCompletionOpenAiModel: String = "",
        var inlineCompletionOpenAiTemperature: Double = 0.1,
        var inlineCompletionCodexCliModel: String = "",
        var inlineCompletionCodexCliTemperature: Double = 0.1,
        var inlineCompletionSuffixChars: Int = 1000,
        var notifyAboutNewModels: Boolean = true,
        var notifyAboutPluginUpdates: Boolean = true,
        var knownOpenAiChatModelIds: MutableList<String> = mutableListOf(),
        var lastOpenAiModelCheckEpochMs: Long = 0L,
        var lastSeenPluginVersion: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): CodexSettingsState = service()
    }
}
