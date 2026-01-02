package com.codex.phpstorm.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiModelsClientTest {

    @Test
    fun `defaultChatModelIds excludes non-chat models like realtime and instruct`() {
        val input = listOf(
            "gpt-realtime-mini-2025-12-15",
            "gpt-3.5-turbo-instruct",
            "text-embedding-3-large",
            "gpt-4.1-mini",
            "gpt-4o-mini",
            "o3-mini",
            "dall-e-3"
        )

        val expected = listOf(
            "gpt-4.1-mini",
            "gpt-4o-mini",
            "o3-mini"
        )
        assertEquals(expected, OpenAiModelsClient.defaultChatModelIds(input))
    }
}

