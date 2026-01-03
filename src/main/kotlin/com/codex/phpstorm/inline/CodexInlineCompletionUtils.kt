package com.codex.phpstorm.inline

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import kotlin.math.max
import kotlin.math.min

object CodexInlineCompletionUtils {

    private const val MAX_PREFIX_CHARS = 4000
    private const val DEFAULT_MAX_SUFFIX_CHARS = 1000
    private const val MAX_SEMANTIC_CONTEXT_CHARS = 8000
    private const val IDEA_COMPLETION_DUMMY = "IntellijIdeaRulezzz"

    fun extractContext(
        text: CharSequence,
        caretOffset: Int,
        maxPrefixChars: Int = MAX_PREFIX_CHARS,
        maxSuffixChars: Int = DEFAULT_MAX_SUFFIX_CHARS
    ): Pair<String, String> {
        val safeOffset = caretOffset.coerceIn(0, text.length)
        val prefixStart = max(0, safeOffset - maxPrefixChars)
        val suffixEnd = min(text.length, safeOffset + maxSuffixChars)
        val prefix = text.subSequence(prefixStart, safeOffset).toString()
        val suffix = text.subSequence(safeOffset, suffixEnd).toString()
        return prefix to suffix
    }

    fun buildContext(file: PsiFile, text: CharSequence, caretOffset: Int, requestedMaxSuffixChars: Int): Pair<String, String> {
        val maxSuffixChars = requestedMaxSuffixChars.coerceIn(200, 8000)
        val semantic = buildPhpSemanticContext(file, text, caretOffset, maxSuffixChars)
        if (semantic != null) return semantic
        return extractContext(text, caretOffset, MAX_PREFIX_CHARS, maxSuffixChars)
    }

    fun shouldTrigger(request: InlineCompletionRequest): Boolean {
        val event = request.event
        if (event is InlineCompletionEvent.DocumentChange) {
            val typed = event.typing.typed
            if (typed.isEmpty()) return false
            val containsNewline = typed.contains('\n')
            val containsNonWhitespace = typed.any { !it.isWhitespace() }
            if (!containsNewline && !containsNonWhitespace) return false
        }
        return true
    }

    fun shouldAutoTriggerOnInsertedText(inserted: String): Boolean {
        if (inserted.isEmpty()) return false
        if (inserted == IDEA_COMPLETION_DUMMY) return false

        val containsNewline = inserted.contains('\n')
        val containsNonWhitespace = inserted.any { !it.isWhitespace() }
        return containsNewline || containsNonWhitespace
    }

    fun sanitizeSuggestion(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        text = stripCodeFences(text).trim()
        text = stripCommonPrefixes(text).trimStart()

        return text
    }

    fun isDuplicateOfSuffix(suggestion: String, suffix: String): Boolean {
        val trimmedSuggestion = suggestion.trimStart()
        if (trimmedSuggestion.isEmpty()) return false

        val trimmedSuffix = suffix.trimStart()
        if (trimmedSuffix.isEmpty()) return false

        if (trimmedSuffix.startsWith(trimmedSuggestion)) return true

        val firstLine = trimmedSuggestion.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isEmpty()) return false
        val canonSuggestion = canonicalLine(firstLine)

        return trimmedSuffix
            .lineSequence()
            .take(50)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { canonicalLine(it) == canonSuggestion }
    }

    fun isEchoingPrefix(suggestion: String, prefix: String): Boolean {
        val trimmedSuggestion = suggestion.trimStart()
        if (trimmedSuggestion.isEmpty()) return false

        val firstLine = trimmedSuggestion.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isEmpty()) return false
        val canonSuggestion = canonicalLine(firstLine)
        val startsWithFunction = canonSuggestion.startsWith("function")

        val recentPrefixLines = prefix
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
            .takeLast(5)

        val recentCanonical = recentPrefixLines.map(::canonicalLine)

        if (startsWithFunction && recentCanonical.any { it.endsWith("function") || it.endsWith("publicfunction") || it.endsWith("protectedfunction") || it.endsWith("privatefunction") }) {
            return true
        }

        val lastPrefix = recentCanonical.lastOrNull().orEmpty()
        if (lastPrefix.endsWith(")") && (canonSuggestion == "()" || canonSuggestion.startsWith("()"))) {
            return true
        }

        return recentCanonical.any { it == canonSuggestion }
    }

    private fun canonicalLine(line: String): String =
        line.filter { !it.isWhitespace() }.lowercase()

    private fun stripCommonPrefixes(text: String): String {
        val trimmed = text.trimStart()
        val prefixes = listOf("Assistant:", "Codex:", "Sure,", "Sure:")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix).trimStart()
            }
        }
        return trimmed
    }

    private fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        val start = trimmed.indexOf("```")
        if (start < 0) return trimmed

        val afterFence = trimmed.indexOf('\n', startIndex = start + 3)
        if (afterFence < 0) return trimmed

        val end = trimmed.indexOf("```", startIndex = afterFence + 1)
        if (end < 0) return trimmed

        return trimmed.substring(afterFence + 1, end)
    }

    private fun buildPhpSemanticContext(
        file: PsiFile,
        text: CharSequence,
        caretOffset: Int,
        maxSuffixChars: Int
    ): Pair<String, String>? {
        val phpFile = file as? PhpFile ?: return null
        val element = phpFile.findElementAt(caretOffset)
            ?: phpFile.findElementAt((caretOffset - 1).coerceAtLeast(0))
            ?: return null

        val method = PsiTreeUtil.getParentOfType(element, Method::class.java, false)
        val function = PsiTreeUtil.getParentOfType(element, Function::class.java, false)
        val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
        val container = (method ?: function ?: phpClass) as? PsiElement ?: return null

        val range = container.textRange
        if (range.length > MAX_SEMANTIC_CONTEXT_CHARS) return null

        val prefixStart = max(range.startOffset, caretOffset - MAX_PREFIX_CHARS)
        val suffixEnd = min(range.endOffset, caretOffset + maxSuffixChars)

        if (prefixStart >= caretOffset) return null
        if (suffixEnd <= caretOffset) return null

        val prefix = text.subSequence(prefixStart, caretOffset).toString()
        val suffix = text.subSequence(caretOffset, suffixEnd).toString()
        if (prefix.isBlank()) return null

        return prefix to suffix
    }
}
