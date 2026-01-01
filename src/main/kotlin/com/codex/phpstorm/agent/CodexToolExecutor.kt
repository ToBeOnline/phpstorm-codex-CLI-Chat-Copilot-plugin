package com.codex.phpstorm.agent

import com.codex.phpstorm.client.ToolCall
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class CodexToolExecutor(private val project: Project) {

    private val json = Json { ignoreUnknownKeys = true }

    data class ToolExecution(
        val toolResponseContent: String,
        val userSummary: String
    )

    fun describe(toolCall: ToolCall): String {
        val name = toolCall.function.name
        val args = toolCall.function.arguments
        return "$name($args)"
    }

    fun execute(toolCall: ToolCall): ToolExecution {
        val settings = CodexSettingsState.getInstance().state
        val args = parseArgs(toolCall.function.arguments)
        return when (toolCall.function.name) {
            "list_files" -> {
                ensureAllowed(settings.allowFileRead, "File listing is disabled in Codex settings.")
                listFiles(args)
            }
            "read_file" -> {
                ensureAllowed(settings.allowFileRead, "File reading is disabled in Codex settings.")
                readFile(args)
            }
            "write_file" -> {
                ensureAllowed(settings.allowFileWrite, "File writing is disabled in Codex settings.")
                writeFile(args)
            }
            "delete_file" -> {
                ensureAllowed(settings.allowFileWrite, "File deletion is disabled in Codex settings.")
                deleteFile(args)
            }
            "run_command" -> {
                ensureAllowed(settings.allowCommandExecution, "Command execution is disabled in Codex settings.")
                runCommand(args)
            }
            else -> ToolExecution(
                toolResponseContent = buildJsonObject {
                    put("error", "Unknown tool: ${toolCall.function.name}")
                }.toString(),
                userSummary = "Unknown tool: ${toolCall.function.name}"
            )
        }
    }

    private fun parseArgs(raw: String): JsonObject {
        return runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }
    }

    private fun ensureAllowed(allowed: Boolean, message: String) {
        if (!allowed) throw IllegalStateException(message)
    }

    private fun projectRoot(): Path {
        val basePath = project.basePath ?: throw IllegalStateException("Project base path is unavailable.")
        return Paths.get(basePath).normalize().toAbsolutePath()
    }

    private fun resolveProjectPath(relative: String): Path {
        val root = projectRoot()
        val resolved = root.resolve(relative).normalize()
        if (!resolved.startsWith(root)) {
            throw IllegalArgumentException("Path escapes project root: $relative")
        }
        return resolved
    }

    private fun listFiles(args: JsonObject): ToolExecution {
        val path = args["path"]?.jsonPrimitive?.content?.ifBlank { "." } ?: "."
        val maxDepth = args["maxDepth"]?.jsonPrimitive?.int ?: 8
        val maxResults = args["maxResults"]?.jsonPrimitive?.int ?: 500

        val root = projectRoot()
        val start = resolveProjectPath(path)
        if (!start.exists() || !start.isDirectory()) {
            throw IllegalArgumentException("Not a directory: $path")
        }

        val files = mutableListOf<String>()
        Files.walk(start, maxDepth).use { stream ->
            val iter = stream.iterator()
            while (iter.hasNext() && files.size < maxResults) {
                val p = iter.next()
                if (!Files.isRegularFile(p)) continue
                val rel = root.relativize(p).toString()
                if (shouldSkip(rel)) continue
                files.add(rel)
            }
        }

        val response = buildJsonObject {
            put("path", path)
            put("count", files.size)
            put("files", files.joinToString("\n"))
        }.toString()
        return ToolExecution(response, "Listed ${files.size} files under $path")
    }

    private fun readFile(args: JsonObject): ToolExecution {
        val path = args["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val maxChars = args["maxChars"]?.jsonPrimitive?.int ?: 20000
        val resolved = resolveProjectPath(path)

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString())
            ?: throw IllegalArgumentException("File not found: $path")
        if (vFile.isDirectory) {
            throw IllegalArgumentException("Path is a directory: $path")
        }

        val document = FileDocumentManager.getInstance().getDocument(vFile)
        val rawText = document?.text ?: VfsUtilCore.loadText(vFile).toString()
        val truncated = rawText.length > maxChars
        val content = if (truncated) rawText.take(maxChars) + "\n...[truncated]..." else rawText

        val response = buildJsonObject {
            put("path", path)
            put("truncated", truncated)
            put("content", content)
        }.toString()
        return ToolExecution(response, "Read $path${if (truncated) " (truncated)" else ""}")
    }

    private fun writeFile(args: JsonObject): ToolExecution {
        val path = args["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val content = args["content"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'content'")
        val overwrite = args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: true

        val resolved = resolveProjectPath(path)
        val dirPath = resolved.parent ?: projectRoot()
        val fileName = resolved.name

        var existed = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val dir = VfsUtil.createDirectories(dirPath.toString())
                val existing = dir.findChild(fileName)
                if (existing != null) {
                    existed = true
                    if (!overwrite) {
                        throw IllegalStateException("Refusing to overwrite existing file: $path")
                    }
                }
                val file = existing ?: dir.createChildData(this, fileName)
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null) {
                    document.setText(content)
                    FileDocumentManager.getInstance().saveDocument(document)
                } else {
                    VfsUtil.saveText(file, content)
                }
                file.refresh(false, false)
            }
        }

        val response = buildJsonObject {
            put("path", path)
            put("existed", existed)
            put("charsWritten", content.length)
        }.toString()
        return ToolExecution(response, "Wrote $path (${content.length} chars)")
    }

    private fun deleteFile(args: JsonObject): ToolExecution {
        val path = args["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val resolved = resolveProjectPath(path)

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString())
            ?: throw IllegalArgumentException("File not found: $path")
        if (vFile.isDirectory) {
            throw IllegalArgumentException("Refusing to delete directory: $path")
        }

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                vFile.delete(this)
            }
        }

        val response = buildJsonObject { put("path", path) }.toString()
        return ToolExecution(response, "Deleted $path")
    }

    private fun runCommand(args: JsonObject): ToolExecution {
        val command = args["command"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'command'")
        val timeoutMs = args["timeoutMs"]?.jsonPrimitive?.int ?: 600000

        val basePath = project.basePath ?: throw IllegalStateException("Project base path is unavailable.")
        val commandLine = if (SystemInfo.isWindows) {
            GeneralCommandLine("cmd.exe", "/c", command).withWorkDirectory(basePath)
        } else {
            GeneralCommandLine("bash", "-lc", command).withWorkDirectory(basePath)
        }

        val output = CapturingProcessHandler(commandLine).runProcess(timeoutMs)

        val stdout = output.stdout.take(20000)
        val stderr = output.stderr.take(20000)

        val response = buildJsonObject {
            put("command", command)
            put("exitCode", output.exitCode)
            put("timeout", output.isTimeout)
            put("stdout", stdout)
            put("stderr", stderr)
        }.toString()
        val userSummary = "Ran command: $command (exit ${output.exitCode}${if (output.isTimeout) ", timeout" else ""})"
        return ToolExecution(response, userSummary)
    }

    private fun shouldSkip(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        return normalized.startsWith(".git/") ||
            normalized.startsWith(".idea/") ||
            normalized.startsWith("build/") ||
            normalized.startsWith("out/") ||
            normalized.startsWith(".gradle/") ||
            normalized.startsWith("node_modules/") ||
            normalized.startsWith("vendor/")
    }

    private val JsonPrimitive.booleanOrNull: Boolean?
        get() = when (content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
}
