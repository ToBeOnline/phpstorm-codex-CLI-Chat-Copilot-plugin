package com.codex.phpstorm.agent

import com.codex.phpstorm.client.ToolDefinition
import com.codex.phpstorm.client.ToolFunction
import com.codex.phpstorm.settings.CodexSettingsState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object CodexToolCatalog {

    fun toolsFor(settings: CodexSettingsState.State): List<ToolDefinition> {
        if (!settings.agentModeEnabled) return emptyList()

        val tools = mutableListOf<ToolDefinition>()
        if (settings.allowFileRead) {
            tools += tool("list_files", "List project files under a path.", listFilesSchema())
            tools += tool("read_file", "Read a UTF-8 text file from the project.", readFileSchema())
        }
        if (settings.allowFileWrite) {
            tools += tool("write_file", "Create or overwrite a UTF-8 text file in the project.", writeFileSchema())
            tools += tool("delete_file", "Delete a file in the project.", deleteFileSchema())
        }
        if (settings.allowCommandExecution) {
            tools += tool("run_command", "Run a local shell command in the project root and return stdout/stderr/exit code.", runCommandSchema())
        }
        return tools
    }

    private fun tool(name: String, description: String, parameters: JsonElement): ToolDefinition {
        return ToolDefinition(function = ToolFunction(name = name, description = description, parameters = parameters))
    }

    private fun listFilesSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Project-relative directory path (default: \".\").")
            }
            putJsonObject("maxDepth") {
                put("type", "integer")
                put("description", "Max directory depth to traverse (default: 8).")
                put("default", 8)
            }
            putJsonObject("maxResults") {
                put("type", "integer")
                put("description", "Max files returned (default: 500).")
                put("default", 500)
            }
        }
        putJsonArray("required") { }
    }

    private fun readFileSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Project-relative path to a file.")
            }
            putJsonObject("maxChars") {
                put("type", "integer")
                put("description", "Max characters returned (default: 20000).")
                put("default", 20000)
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }

    private fun writeFileSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Project-relative file path to write.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Full UTF-8 file content.")
            }
            putJsonObject("overwrite") {
                put("type", "boolean")
                put("description", "Whether to overwrite if the file exists (default: true).")
                put("default", true)
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("path"))
            add(JsonPrimitive("content"))
        }
    }

    private fun deleteFileSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Project-relative file path to delete.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }

    private fun runCommandSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "string")
                put("description", "Shell command line. Runs via bash -lc (macOS/Linux) or cmd /c (Windows).")
            }
            putJsonObject("timeoutMs") {
                put("type", "integer")
                put("description", "Timeout in milliseconds (default: 600000).")
                put("default", 600000)
            }
        }
        putJsonArray("required") { add(JsonPrimitive("command")) }
    }
}
