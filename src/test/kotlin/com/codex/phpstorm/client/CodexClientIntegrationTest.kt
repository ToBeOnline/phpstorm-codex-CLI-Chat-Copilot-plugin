package com.codex.phpstorm.client

import com.codex.phpstorm.agent.CodexToolCatalog
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class CodexClientIntegrationTest : BasePlatformTestCase() {

    private var server: HttpServer? = null

    override fun tearDown() {
        try {
            server?.stop(0)
            server = null
        } finally {
            super.tearDown()
        }
    }

    fun testCreateChatCompletionParsesAssistantContent() {
        val callCount = AtomicInteger(0)
        val port = startServer(callCount, handler = { exchange ->
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            assertTrue(exchange.requestMethod.equals("POST", ignoreCase = true))
            assertEquals("/v1/chat/completions", exchange.requestURI.path)
            assertTrue(body.contains("\"messages\""))

            respondJson(
                exchange,
                """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"hello"}}]}
                """.trimIndent()
            )
        })

        val settingsService = CodexSettingsState.getInstance()
        val original = settingsService.state.copy()
        try {
            settingsService.loadState(
                original.copy(
                    apiBaseUrl = "http://127.0.0.1:$port/v1",
                    apiKey = "",
                    model = "gpt-4.1"
                )
            )

            val message = CodexClient.getInstance(project)
                .createChatCompletion(
                    messages = listOf(ChatCompletionMessage(role = "user", content = "hi")),
                    tools = null
                )
                .getOrThrow()

            assertEquals("assistant", message.role)
            assertEquals("hello", message.content)
            assertEquals(1, callCount.get())
        } finally {
            settingsService.loadState(original)
        }
    }

    fun testCreateChatCompletionParsesToolCalls() {
        val callCount = AtomicInteger(0)
        val port = startServer(callCount, handler = { exchange ->
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val json = Json.parseToJsonElement(body).jsonObject
            assertNotNull(json["tools"])

            respondJson(
                exchange,
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "read_file",
                              "arguments": "{\"path\":\"README.md\",\"maxChars\":120}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        })

        val settingsService = CodexSettingsState.getInstance()
        val original = settingsService.state.copy()
        try {
            val configured = original.copy(
                apiBaseUrl = "http://127.0.0.1:$port/v1",
                apiKey = "",
                model = "gpt-4.1",
                agentModeEnabled = true,
                allowFileRead = true
            )
            settingsService.loadState(configured)

            val tools = CodexToolCatalog.toolsFor(configured)
            val message = CodexClient.getInstance(project)
                .createChatCompletion(
                    messages = listOf(ChatCompletionMessage(role = "user", content = "list files")),
                    tools = tools
                )
                .getOrThrow()

            assertEquals("assistant", message.role)
            assertTrue(message.toolCalls?.isNotEmpty() == true)
            assertEquals("read_file", message.toolCalls!!.first().function.name)
            assertEquals(1, callCount.get())
        } finally {
            settingsService.loadState(original)
        }
    }

    private fun startServer(counter: AtomicInteger, handler: (HttpExchange) -> Unit): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions", HttpHandler { exchange ->
            counter.incrementAndGet()
            try {
                handler(exchange)
            } catch (t: Throwable) {
                val msg = ("Test server error: ${t.message}").toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(500, msg.size.toLong())
                exchange.responseBody.use { it.write(msg) }
            } finally {
                exchange.close()
            }
        })
        server.start()
        this.server = server
        return server.address.port
    }

    private fun respondJson(exchange: HttpExchange, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
