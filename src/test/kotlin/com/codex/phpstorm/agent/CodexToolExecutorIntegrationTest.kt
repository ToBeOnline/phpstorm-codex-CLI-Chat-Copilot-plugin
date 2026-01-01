package com.codex.phpstorm.agent

import com.codex.phpstorm.client.ToolCall
import com.codex.phpstorm.client.ToolCallFunction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Paths

class CodexToolExecutorIntegrationTest : BasePlatformTestCase() {

    fun testWriteReadDeleteFile() {
        val basePath = project.basePath
        assertTrue(!basePath.isNullOrBlank())

        val executor = CodexToolExecutor(project)

        val writeCall = ToolCall(
            id = "call_write",
            type = "function",
            function = ToolCallFunction(
                name = "write_file",
                arguments = """{"path":"src/test-data/hello.txt","content":"Hello from Codex","overwrite":true}"""
            )
        )
        executor.execute(writeCall)

        val writtenPath = Paths.get(basePath!!, "src/test-data/hello.txt")
        assertTrue(Files.exists(writtenPath))

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(writtenPath.toString())
        assertTrue(vFile != null && !vFile.isDirectory)
        assertEquals("Hello from Codex", VfsUtilCore.loadText(vFile!!).toString())

        val readCall = ToolCall(
            id = "call_read",
            type = "function",
            function = ToolCallFunction(
                name = "read_file",
                arguments = """{"path":"src/test-data/hello.txt","maxChars":1000}"""
            )
        )
        val readResult = executor.execute(readCall)
        assertTrue(readResult.toolResponseContent.contains("Hello from Codex"))

        val deleteCall = ToolCall(
            id = "call_delete",
            type = "function",
            function = ToolCallFunction(
                name = "delete_file",
                arguments = """{"path":"src/test-data/hello.txt"}"""
            )
        )
        executor.execute(deleteCall)

        assertFalse(Files.exists(writtenPath))
    }
}

