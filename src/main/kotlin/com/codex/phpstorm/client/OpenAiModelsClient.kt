package com.codex.phpstorm.client

import com.intellij.util.io.HttpRequests
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection

object OpenAiModelsClient {

    private val json = Json { ignoreUnknownKeys = true }

    internal fun listModels(apiBaseUrl: String, apiKey: String): Result<List<OpenAiModelInfo>> {
        val url = buildModelsUrl(apiBaseUrl)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("OpenAI API key is required to fetch models."))
        }

        return runCatching {
            HttpRequests.request(url)
                .productNameAsUserAgent()
                .accept("application/json")
                .throwStatusCodeException(false)
                .tuner { connection ->
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                .connect { request ->
                    val connection = request.connection as? HttpURLConnection
                    val status = connection?.responseCode ?: -1
                    val responseText = (if (status >= 400) request.readError() else request.readString()).orEmpty()
                    if (status >= 400) {
                        val requestId = connection?.getHeaderField("x-request-id")?.takeIf { it.isNotBlank() }
                        val suffix = if (requestId == null) "" else " (request_id=$requestId)"
                        val errorMessage = extractErrorMessage(responseText)
                        throw IllegalStateException("OpenAI models request failed: HTTP $status: $errorMessage$suffix")
                    }

                    val response = json.decodeFromString(OpenAiModelsResponse.serializer(), responseText)
                    response.data
                        .mapNotNull { model ->
                            model.id.takeIf(String::isNotBlank)?.let { OpenAiModelInfo(id = it, created = model.created) }
                        }
                        .sortedWith(
                            compareByDescending<OpenAiModelInfo> { it.created }
                                .thenBy { it.id }
                        )
                }
        }
    }

    internal fun buildModelsUrl(apiBaseUrl: String): String {
        val trimmed = apiBaseUrl.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return "https://api.openai.com/v1/models"

        val withoutKnownEndpointSuffix = when {
            trimmed.endsWith("/chat/completions") -> trimmed.removeSuffix("/chat/completions")
            trimmed.endsWith("/responses") -> trimmed.removeSuffix("/responses")
            else -> trimmed
        }.removeSuffix("/")

        return when {
            withoutKnownEndpointSuffix.endsWith("/models") -> withoutKnownEndpointSuffix
            withoutKnownEndpointSuffix.endsWith("/v1") -> "$withoutKnownEndpointSuffix/models"
            withoutKnownEndpointSuffix.contains("/v1/") -> "${withoutKnownEndpointSuffix.substringBefore("/v1/")}/v1/models"
            else -> "$withoutKnownEndpointSuffix/models"
        }
    }

    internal fun defaultChatModelIds(modelIds: List<String>): List<String> {
        val include = Regex("^(gpt-|chatgpt-|o\\d)", RegexOption.IGNORE_CASE)
        val exclude = Regex("(realtime|instruct|transcribe|tts|whisper|embedding|dall-e|image)", RegexOption.IGNORE_CASE)
        return modelIds.filter { include.containsMatchIn(it) && !exclude.containsMatchIn(it) }
    }

    private fun extractErrorMessage(responseText: String): String {
        val trimmed = responseText.trim()
        if (trimmed.isEmpty()) return "Empty error response"
        return runCatching {
            val parsed = json.decodeFromString(OpenAiErrorResponse.serializer(), trimmed)
            parsed.error.message?.takeIf { it.isNotBlank() } ?: trimmed
        }.getOrDefault(trimmed)
    }
}

internal data class OpenAiModelInfo(
    val id: String,
    val created: Long
)

@Serializable
private data class OpenAiModelsResponse(
    val data: List<Model> = emptyList()
) {
    @Serializable
    data class Model(
        val id: String = "",
        val created: Long = 0
    )
}
