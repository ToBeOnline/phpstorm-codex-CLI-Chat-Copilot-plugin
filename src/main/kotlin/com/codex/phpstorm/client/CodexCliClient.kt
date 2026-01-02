package com.codex.phpstorm.client

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class CodexCliClient(private val project: Project) {

    private val logger = Logger.getInstance(CodexCliClient::class.java)

    data class CliResult(
        val text: String,
        val threadId: String?
    )

    fun chat(messages: List<ChatCompletionMessage>, modelOverride: String? = null, temperatureHint: Double? = null): Result<CliResult> {
        val settings = CodexSettingsState.getInstance().state
        val prompt = buildPrompt(messages, temperatureHint)

        val basePath = project.basePath ?: return Result.failure(IllegalStateException("Project base path is unavailable."))
        val exe = settings.codexCliPath.ifBlank { "codex" }
        val extra = splitArgs(settings.codexCliExtraArgs)
        val modelArgs = buildModelArgs(modelOverride?.trim().takeIf { !it.isNullOrBlank() } ?: settings.codexCliModel, extra)

        val commandLine = GeneralCommandLine(exe)
            .withWorkDirectory(basePath)
            .withParameters(listOf("exec") + modelArgs + extra + listOf("--color", "never", "--json", "--skip-git-repo-check", prompt))

        return runCatching {
            val output = CapturingProcessHandler(commandLine).runProcess(settings.codexCliTimeoutMs)
            if (output.exitCode != 0) {
                throw IllegalStateException(
                    buildString {
                        append("Codex CLI failed (exit ${output.exitCode})")
                        if (output.stderr.isNotBlank()) append(": ").append(output.stderr.trim())
                    }
                )
            }

            val parsed = CodexCliJsonParser.parse(output.stdout)
            val text = parsed.lastAgentMessage
                ?: throw IllegalStateException("Codex CLI did not return an agent_message. Raw output: ${output.stdout.take(2000)}")
            CliResult(text = text, threadId = parsed.threadId)
        }.onFailure { logger.warn("Codex CLI chat failed: ${it.message}", it) }
    }

    private fun buildPrompt(messages: List<ChatCompletionMessage>, temperatureHint: Double?): String {
        val sb = StringBuilder()
        sb.append(
            """
            Continue the following chat conversation. Respond as the assistant.
            Do not include metadata or prefixes like "User:" in your reply unless asked.
            
            """.trimIndent()
        )
        if (temperatureHint != null) {
            val t = temperatureHint.coerceIn(0.0, 1.0)
            sb.append("Temperature hint: ").append(String.format("%.2f", t)).append(" (lower = more deterministic).\n\n")
        }
        for (m in messages) {
            val content = m.content ?: continue
            if (m.role == "tool") continue
            when (m.role) {
                "system" -> sb.append("System: ").append(content.trim()).append("\n\n")
                "user" -> sb.append("User: ").append(content.trim()).append("\n\n")
                "assistant" -> sb.append("Assistant: ").append(content.trim()).append("\n\n")
                else -> sb.append(m.role).append(": ").append(content.trim()).append("\n\n")
            }
        }
        sb.append("Assistant:")
        return sb.toString()
    }

    private fun splitArgs(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaped = false

        for (c in trimmed) {
            if (escaped) {
                current.append(c)
                escaped = false
                continue
            }
            if (c == '\\' && inDouble) {
                escaped = true
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                continue
            }
            if (!inSingle && !inDouble && c.isWhitespace()) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.setLength(0)
                }
                continue
            }
            current.append(c)
        }

        if (inSingle || inDouble) {
            throw IllegalArgumentException("Unclosed quote in Codex CLI extra args.")
        }
        if (escaped) {
            current.append('\\')
        }
        if (current.isNotEmpty()) {
            result += current.toString()
        }
        return result
    }

    private fun buildModelArgs(model: String, extraArgs: List<String>): List<String> {
        val trimmedModel = model.trim()
        if (trimmedModel.isEmpty()) return emptyList()
        val alreadyHasModel = extraArgs.any { it == "-m" || it == "--model" || it.startsWith("--model=") }
        return if (alreadyHasModel) emptyList() else listOf("-m", trimmedModel)
    }

    companion object {
        fun getInstance(project: Project): CodexCliClient = project.service()
    }
}
