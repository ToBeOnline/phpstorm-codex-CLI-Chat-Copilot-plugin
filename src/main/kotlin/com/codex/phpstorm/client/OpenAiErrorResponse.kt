package com.codex.phpstorm.client

import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiErrorResponse(
    val error: Error = Error()
) {
    @Serializable
    data class Error(
        val message: String? = null,
        val type: String? = null,
        val param: String? = null,
        val code: String? = null
    )
}

