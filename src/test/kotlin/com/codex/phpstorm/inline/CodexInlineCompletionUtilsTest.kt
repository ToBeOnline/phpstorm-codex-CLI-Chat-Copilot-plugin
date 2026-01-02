package com.codex.phpstorm.inline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexInlineCompletionUtilsTest {

    @Test
    fun `extractContext splits prefix and suffix around caret`() {
        val (prefix, suffix) = CodexInlineCompletionUtils.extractContext("hello world", 5)
        assertEquals("hello", prefix)
        assertEquals(" world", suffix)
    }

    @Test
    fun `sanitizeSuggestion strips code fences`() {
        val raw = "```php\n<?php echo 'hi';\n```"
        assertEquals("<?php echo 'hi';", CodexInlineCompletionUtils.sanitizeSuggestion(raw))
    }

    @Test
    fun `sanitizeSuggestion strips common prefixes`() {
        assertEquals("hello", CodexInlineCompletionUtils.sanitizeSuggestion("Assistant: hello"))
    }

    @Test
    fun `shouldAutoTriggerOnInsertedText ignores blanks and IDE completion dummy`() {
        assertFalse(CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText(""))
        assertFalse(CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText("   "))
        assertFalse(CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText("IntellijIdeaRulezzz"))

        assertTrue(CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText("\n"))
        assertTrue(CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText("a"))
    }

    @Test
    fun `isDuplicateOfSuffix detects overlap at top of suffix`() {
        val suffix = "\npublic function __construct() {}\nclass Foo {}\n"
        assertTrue(CodexInlineCompletionUtils.isDuplicateOfSuffix("public function __construct() {}", suffix))
        assertFalse(CodexInlineCompletionUtils.isDuplicateOfSuffix("public function other() {}", suffix))
    }

    @Test
    fun `isEchoingPrefix detects repeats of recent prefix lines`() {
        val prefix = "line1\nline2\nline3\nif (!defined('ABSPATH')) {\n"
        assertTrue(CodexInlineCompletionUtils.isEchoingPrefix("if (!defined('ABSPATH')) {\n// body", prefix))
        assertFalse(CodexInlineCompletionUtils.isEchoingPrefix("public function other() {}", prefix))
    }
}
