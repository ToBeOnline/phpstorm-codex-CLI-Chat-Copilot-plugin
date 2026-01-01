package com.codex.phpstorm.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodexCliJsonParserTest {

    @Test
    fun `parses thread id and last agent message`() {
        val input = """
            {"type":"thread.started","thread_id":"thread_123"}
            {"type":"turn.started"}
            {"type":"item.completed","item":{"id":"item_0","type":"agent_message","text":"Hello"}}
            {"type":"turn.completed","usage":{"input_tokens":1,"output_tokens":1}}
        """.trimIndent()

        val parsed = CodexCliJsonParser.parse(input)
        assertEquals("thread_123", parsed.threadId)
        assertEquals("Hello", parsed.lastAgentMessage)
        assertEquals(listOf("Hello"), parsed.allAgentMessages)
    }

    @Test
    fun `handles multiple agent messages`() {
        val input = """
            {"type":"thread.started","thread_id":"thread_123"}
            {"type":"item.completed","item":{"id":"item_0","type":"agent_message","text":"First"}}
            {"type":"item.completed","item":{"id":"item_1","type":"agent_message","text":"Second"}}
        """.trimIndent()

        val parsed = CodexCliJsonParser.parse(input)
        assertEquals("Second", parsed.lastAgentMessage)
        assertEquals(listOf("First", "Second"), parsed.allAgentMessages)
    }

    @Test
    fun `returns nulls when no agent message present`() {
        val input = """
            {"type":"thread.started","thread_id":"thread_123"}
            {"type":"turn.started"}
        """.trimIndent()

        val parsed = CodexCliJsonParser.parse(input)
        assertEquals("thread_123", parsed.threadId)
        assertNull(parsed.lastAgentMessage)
        assertEquals(emptyList<String>(), parsed.allAgentMessages)
    }
}

