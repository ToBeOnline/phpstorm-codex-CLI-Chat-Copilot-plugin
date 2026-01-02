package com.codex.phpstorm.startup

import com.codex.phpstorm.client.OpenAiModelInfo
import com.codex.phpstorm.client.OpenAiModelsClient
import com.codex.phpstorm.inline.CodexInlineCompletionAutoTriggerService
import com.codex.phpstorm.notifications.CodexNotifier
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CodexStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        if (!hasRun.compareAndSet(false, true)) return

        ApplicationManager.getApplication().service<CodexInlineCompletionAutoTriggerService>()

        val settings = CodexSettingsState.getInstance().state
        val currentVersion = currentPluginVersion().orEmpty()

        if (currentVersion.isNotBlank()) {
            maybeNotifyPluginUpdate(project, settings, currentVersion)
        }

        maybeCheckForNewModels(project)
    }

    private fun maybeNotifyPluginUpdate(project: Project, settings: CodexSettingsState.State, currentVersion: String) {
        val lastSeen = settings.lastSeenPluginVersion.trim()
        settings.lastSeenPluginVersion = currentVersion

        if (!settings.notifyAboutPluginUpdates) return
        if (lastSeen.isBlank() || lastSeen == currentVersion) return

        CodexNotifier.info(project, "Codex Chat updated to $currentVersion (was $lastSeen). Check Settings/Preferences | Tools | Codex for new options.")
    }

    private fun maybeCheckForNewModels(project: Project) {
        val settings = CodexSettingsState.getInstance().state
        if (!settings.notifyAboutNewModels) return

        val apiBaseUrl = settings.apiBaseUrl.trim()
        val apiKey = settings.apiKey
        if (apiBaseUrl.isBlank() || apiKey.isBlank()) return

        val now = System.currentTimeMillis()
        val intervalMs = TimeUnit.HOURS.toMillis(24)
        val lastCheck = settings.lastOpenAiModelCheckEpochMs
        if (lastCheck > 0 && now - lastCheck < intervalMs) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Codex: Check for new models", false) {
            private var result: Result<List<OpenAiModelInfo>> = Result.success(emptyList())

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                result = OpenAiModelsClient.listModels(apiBaseUrl, apiKey)
            }

            override fun onFinished() {
                result.onSuccess { models ->
                    val chatIds = OpenAiModelsClient
                        .defaultChatModelIds(models.map { it.id })
                        .distinct()

                    val known = settings.knownOpenAiChatModelIds.toSet()
                    settings.knownOpenAiChatModelIds = chatIds.toMutableList()
                    settings.lastOpenAiModelCheckEpochMs = now

                    if (known.isEmpty()) return@onSuccess

                    val newModels = chatIds.filter { it !in known }
                    if (newModels.isEmpty()) return@onSuccess

                    val preview = newModels.take(6)
                    val suffix = if (newModels.size > preview.size) " (+${newModels.size - preview.size} more)" else ""
                    CodexNotifier.info(project, "New OpenAI models available: ${preview.joinToString(", ")}$suffix")
                }
            }
        })
    }

    private fun currentPluginVersion(): String? =
        PluginManagerCore.getPlugin(PLUGIN_ID)?.version?.takeIf { it.isNotBlank() }

    companion object {
        private val hasRun = AtomicBoolean(false)
        private val PLUGIN_ID = PluginId.getId("com.codex.phpstorm")
    }
}
