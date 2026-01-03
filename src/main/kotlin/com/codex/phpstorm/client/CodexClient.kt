package com.codex.phpstorm.client

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.HttpURLConnection

@Service(Service.Level.PROJECT)
class CodexClient(private val project: Project) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val logger = Logger.getInstance(CodexClient::class.java)

    fun createChatCompletion(
        messages: List<ChatCompletionMessage>,
        tools: List<ToolDefinition>? = null,
        maxTokens: Int? = null,
        modelOverride: String? = null,
        temperatureOverride: Double? = null
    ): Result<ChatCompletionMessage> {
        val settings = CodexSettingsState.getInstance().state
        if (settings.apiBaseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Codex API base URL is not configured"))
        }

        val payload = ChatCompletionRequest(
            model = modelOverride?.trim().takeIf { !it.isNullOrBlank() } ?: settings.model,
            temperature = temperatureOverride ?: settings.temperature,
            messages = messages,
            maxTokens = maxTokens,
            tools = tools,
            toolChoice = if (tools.isNullOrEmpty()) null else "auto"
        )
        val url = buildUrl(settings.apiBaseUrl)
        val body = json.encodeToString(ChatCompletionRequest.serializer(), payload)

        return runCatching {
            HttpRequests.post(url, "application/json")
                .productNameAsUserAgent()
                .throwStatusCodeException(false)
                .tuner { connection ->
                    connection.setRequestProperty("Accept", "application/json")
                    if (settings.apiKey.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                    }
                }
                .connect { request ->
                    request.write(body)
                    val connection = request.connection as? HttpURLConnection
                    val status = connection?.responseCode ?: -1
                    val responseText = (if (status >= 400) request.readError() else request.readString()).orEmpty()

                    if (status >= 400) {
                        val requestId = connection?.getHeaderField("x-request-id")?.takeIf { it.isNotBlank() }
                        val errorMessage = extractErrorMessage(responseText)
                        val suffix = if (requestId == null) "" else " (request_id=$requestId)"
                        throw IllegalStateException("Codex API request failed: HTTP $status: $errorMessage$suffix")
                    }
                    if (responseText.isBlank()) {
                        throw IllegalStateException("Codex API response was empty")
                    }
                    parseMessage(responseText)
                }
        }.onFailure { logger.warn("Codex chat request failed: ${it.message}", it) }
    }

    private fun parseMessage(responseText: String): ChatCompletionMessage {
        val response = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
        val message = response.choices.firstOrNull()?.message
        if (message == null) {
            throw IllegalStateException("Codex response did not include a message")
        }
        if (message.content.isNullOrBlank() && message.toolCalls.isNullOrEmpty()) {
            throw IllegalStateException("Codex response message was empty")
        }
        return message
    }

    private fun extractErrorMessage(responseText: String): String {
        val trimmed = responseText.trim()
        if (trimmed.isEmpty()) return "Empty error response"

        return runCatching {
            val parsed = json.decodeFromString(OpenAiErrorResponse.serializer(), trimmed)
            val message = parsed.error.message?.takeIf { it.isNotBlank() } ?: trimmed
            val hint = when (parsed.error.param) {
                "model" -> " (check Settings/Preferences | Tools | Codex | Model)"
                else -> ""
            }
            message + hint
        }.getOrDefault(trimmed)
    }

    private fun buildUrl(base: String): String {
        val sanitized = base.removeSuffix("/")
        return if (sanitized.endsWith("/chat/completions")) sanitized else "$sanitized/chat/completions"
    }

    companion object {
        fun getInstance(project: Project): CodexClient = project.service()
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatCompletionMessage>,
    val temperature: Double = 0.2,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList()
) {
    @Serializable
    data class Choice(
        val index: Int = 0,
        @SerialName("finish_reason")
        val finishReason: String? = null,
        val message: ChatCompletionMessage? = null
    )
}

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)
