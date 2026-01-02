package com.codex.phpstorm.client

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class OpenAiModelsClientIntegrationTest : BasePlatformTestCase() {

    private var server: HttpServer? = null

    override fun tearDown() {
        try {
            server?.stop(0)
            server = null
        } finally {
            super.tearDown()
        }
    }

    fun testBuildModelsUrlNormalizesCommonBases() {
        assertEquals(
            "https://api.openai.com/v1/models",
            OpenAiModelsClient.buildModelsUrl("https://api.openai.com/v1")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            OpenAiModelsClient.buildModelsUrl("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "http://localhost:8700/v1/models",
            OpenAiModelsClient.buildModelsUrl("http://localhost:8700/v1/")
        )
    }

    fun testListModelsParsesModelIds() {
        val callCount = AtomicInteger(0)
        val port = startServer(callCount) { exchange ->
            assertTrue(exchange.requestMethod.equals("GET", ignoreCase = true))
            assertEquals("/v1/models", exchange.requestURI.path)
            assertEquals("Bearer test-key", exchange.requestHeaders.getFirst("Authorization"))

            respondJson(
                exchange,
                """
                {
                  "object": "list",
                  "data": [
                    {"id": "gpt-4o-mini", "created": 200, "object": "model"},
                    {"id": "text-embedding-3-small", "created": 100, "object": "model"}
                  ]
                }
                """.trimIndent()
            )
        }

        val models = OpenAiModelsClient
            .listModels("http://127.0.0.1:$port/v1", "test-key")
            .getOrThrow()

        assertTrue(models.any { it.id == "gpt-4o-mini" })
        assertTrue(models.any { it.id == "text-embedding-3-small" })
        assertEquals("gpt-4o-mini", models.first().id)
        assertEquals(1, callCount.get())
    }

    private fun startServer(counter: AtomicInteger, handler: (HttpExchange) -> Unit): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models", HttpHandler { exchange ->
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
