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
        var apiBaseUrl: String = "http://localhost:8700/v1",
        var apiKey: String = "",
        var model: String = "codex-chat",
        var temperature: Double = 0.2,
        var systemPrompt: String = "You are Codex, a coding assistant integrated inside PhpStorm.",
        var agentModeEnabled: Boolean = false,
        var allowFileRead: Boolean = true,
        var allowFileWrite: Boolean = true,
        var allowCommandExecution: Boolean = false
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
