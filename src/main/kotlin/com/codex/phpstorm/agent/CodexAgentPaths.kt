package com.codex.phpstorm.agent

import java.nio.file.Path

object CodexAgentPaths {

    fun resolveWithinRoot(root: Path, relativePath: String): Path {
        val normalizedRoot = root.normalize().toAbsolutePath()
        val resolved = normalizedRoot.resolve(relativePath).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw IllegalArgumentException("Path escapes project root: $relativePath")
        }
        return resolved
    }

    fun shouldSkip(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        return normalized.startsWith(".git/") ||
            normalized.startsWith(".idea/") ||
            normalized.startsWith("build/") ||
            normalized.startsWith("out/") ||
            normalized.startsWith(".gradle/") ||
            normalized.startsWith("node_modules/") ||
            normalized.startsWith("vendor/")
    }
}

