package com.codex.phpstorm.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class CodexAgentPathsTest {

    @Test
    fun `resolveWithinRoot blocks path traversal`() {
        val root = Files.createTempDirectory("codex-agent-paths").toAbsolutePath().normalize()
        assertThrows(IllegalArgumentException::class.java) {
            CodexAgentPaths.resolveWithinRoot(root, "../escape.txt")
        }
    }

    @Test
    fun `resolveWithinRoot blocks absolute paths`() {
        val root = Files.createTempDirectory("codex-agent-paths").toAbsolutePath().normalize()
        assertThrows(IllegalArgumentException::class.java) {
            CodexAgentPaths.resolveWithinRoot(root, "/etc/passwd")
        }
    }

    @Test
    fun `resolveWithinRoot normalizes inside root`() {
        val root = Files.createTempDirectory("codex-agent-paths").toAbsolutePath().normalize()
        val resolved = CodexAgentPaths.resolveWithinRoot(root, "a/../b/file.txt")
        assertTrue(resolved.startsWith(root))
        assertEquals(root.resolve(Paths.get("b/file.txt")).normalize(), resolved)
    }

    @Test
    fun `shouldSkip filters known noisy directories`() {
        assertTrue(CodexAgentPaths.shouldSkip(".git/config"))
        assertTrue(CodexAgentPaths.shouldSkip(".idea/workspace.xml"))
        assertTrue(CodexAgentPaths.shouldSkip("build/output.txt"))
        assertTrue(CodexAgentPaths.shouldSkip("node_modules/pkg/index.js"))
        assertTrue(CodexAgentPaths.shouldSkip("vendor/autoload.php"))
    }
}

