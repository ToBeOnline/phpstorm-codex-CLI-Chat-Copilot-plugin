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

@Service(Service.Level.PROJECT)
class CodexClient(private val project: Project) {

    private val json = Json { ignoreUnknownKeys = true }
    private val logger = Logger.getInstance(CodexClient::class.java)

    fun createChatCompletion(
        messages: List<ChatCompletionMessage>,
        tools: List<ToolDefinition>? = null
    ): Result<ChatCompletionMessage> {
        val settings = CodexSettingsState.getInstance().state
        if (settings.apiBaseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Codex API base URL is not configured"))
        }

        val payload = ChatCompletionRequest(
            model = settings.model,
            temperature = settings.temperature,
            messages = messages,
            tools = tools,
            toolChoice = if (tools.isNullOrEmpty()) null else "auto"
        )
        val url = buildUrl(settings.apiBaseUrl)
        val body = json.encodeToString(ChatCompletionRequest.serializer(), payload)

        return runCatching {
            HttpRequests.post(url, "application/json")
                .productNameAsUserAgent()
                .tuner { connection ->
                    connection.setRequestProperty("Accept", "application/json")
                    if (settings.apiKey.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                    }
                }
                .connect { request ->
                    request.write(body)
                    val responseText = request.reader.readText()
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
