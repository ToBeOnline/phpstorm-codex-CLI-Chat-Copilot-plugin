package com.codex.phpstorm.inline

import com.codex.phpstorm.client.CodexCliClient
import com.codex.phpstorm.client.CodexClient
import com.codex.phpstorm.notifications.CodexNotifier
import com.codex.phpstorm.settings.CodexBackend
import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CodexInlineCompletionProvider : DebouncedInlineCompletionProvider() {

    private val logger = Logger.getInstance(CodexInlineCompletionProvider::class.java)

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("codex.inline")

    override val delay: Duration = 350.milliseconds

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        val settings = CodexSettingsState.getInstance().state
        if (!settings.inlineCompletionEnabled) return false

        return when (event) {
            is InlineCompletionEvent.DirectCall -> true
            else -> false
        }
    }

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val settings = CodexSettingsState.getInstance().state
        if (!settings.inlineCompletionEnabled) return InlineCompletionSuggestion.empty()

        if (!CodexInlineCompletionUtils.shouldTrigger(request)) return InlineCompletionSuggestion.empty()

        val backend = runCatching { CodexBackend.valueOf(settings.backend) }.getOrDefault(CodexBackend.OPENAI_API)
        if (backend == CodexBackend.OPENAI_API && (settings.apiBaseUrl.isBlank() || settings.apiKey.isBlank())) {
            warnMissingApiConfigOnce()
            return InlineCompletionSuggestion.empty()
        }

        val inlineModel = when (backend) {
            CodexBackend.OPENAI_API -> settings.inlineCompletionOpenAiModel.trim().ifEmpty { settings.model }
            CodexBackend.CODEX_CLI -> settings.inlineCompletionCodexCliModel.trim().ifEmpty { settings.codexCliModel }
        }

        val inlineTemperature = when (backend) {
            CodexBackend.OPENAI_API -> settings.inlineCompletionOpenAiTemperature
            CodexBackend.CODEX_CLI -> settings.inlineCompletionCodexCliTemperature
        }.coerceIn(0.0, 1.0)

        val prepared = ApplicationManager.getApplication().runReadAction<PreparedInlineCompletion?> {
            val editor = request.editor
            if (editor.selectionModel.hasSelection()) return@runReadAction null
            if (editor.caretModel.caretCount != 1) return@runReadAction null

            val caretOffset = editor.caretModel.offset
            val text = request.document.charsSequence
            if (caretOffset < 0 || caretOffset > text.length) return@runReadAction null

            val file = request.file
            val element = file.findElementAt(caretOffset)
                ?: file.findElementAt((caretOffset - 1).coerceAtLeast(0))
            val method = PsiTreeUtil.getParentOfType(element, Method::class.java, false)
            val function = PsiTreeUtil.getParentOfType(element, Function::class.java, false)
            val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
            val atClassLevel = method == null && function == null && phpClass != null
            val maxSuffixChars = settings.inlineCompletionSuffixChars.coerceIn(200, 8000)
            val (prefix, suffix) = CodexInlineCompletionUtils.buildContext(file, text, caretOffset, maxSuffixChars)
            val existingFunctionNames = CodexInlineCompletionUtils.collectFunctionNames(file)
            if (prefix.isBlank()) return@runReadAction null

            val project = file.project
            val messages = CodexInlineCompletionPrompt.buildMessages(settings.systemPrompt, file, prefix, suffix)
            PreparedInlineCompletion(project, messages, prefix, suffix, existingFunctionNames, atClassLevel)
        } ?: return InlineCompletionSuggestion.empty()

        val project = prepared.project
        val messages = prepared.messages

        val rawSuggestionResult = withContext(Dispatchers.IO) {
            when (backend) {
                CodexBackend.OPENAI_API ->
                    CodexClient.getInstance(project)
                        .createChatCompletion(
                            messages,
                            tools = null,
                            maxTokens = 128,
                            modelOverride = inlineModel,
                            temperatureOverride = inlineTemperature
                        )
                        .map { it.content.orEmpty() }
                CodexBackend.CODEX_CLI ->
                    CodexCliClient.getInstance(project)
                        .chat(messages, modelOverride = inlineModel, temperatureHint = inlineTemperature)
                        .map { it.text }
            }
        }

        val rawSuggestion = rawSuggestionResult.getOrElse { error ->
            logger.info("Inline completion failed ($backend, model=$inlineModel): ${error.message}", error)
            maybeNotifyInlineCompletionError(project, backend, error)
            return InlineCompletionSuggestion.empty()
        }

        var suggestion = CodexInlineCompletionUtils.sanitizeSuggestion(rawSuggestion)
        suggestion = CodexInlineCompletionUtils.stripLeadingEmptyParensIfPrefixEndsWithParen(prepared.prefix, suggestion)
        if (suggestion.isBlank()) return InlineCompletionSuggestion.empty()
        val suggestedFunctionName = CodexInlineCompletionUtils.extractFunctionName(suggestion)
        if (suggestedFunctionName != null) {
            val name = suggestedFunctionName
            if (prepared.existingFunctionNames.contains(name)) return InlineCompletionSuggestion.empty()
            if (CodexInlineCompletionUtils.suffixContainsFunctionName(prepared.suffix, name)) return InlineCompletionSuggestion.empty()
        }
        if (prepared.atClassLevel) {
            val firstLine = suggestion.lineSequence().firstOrNull()?.trimStart().orEmpty()
            if (firstLine.isNotEmpty() && !CodexInlineCompletionUtils.isClassMemberDeclaration(firstLine)) {
                return InlineCompletionSuggestion.empty()
            }
        }
        if (CodexInlineCompletionUtils.isDuplicateOfSuffix(suggestion, prepared.suffix) ||
            CodexInlineCompletionUtils.isEchoingPrefix(suggestion, prepared.prefix)
        ) {
            return InlineCompletionSuggestion.empty()
        }

        val clipped = suggestion.take(MAX_SUGGESTION_CHARS)
        return InlineCompletionSuggestion.Default(flowOf(InlineCompletionGrayTextElement(clipped)))
    }

    private fun maybeNotifyInlineCompletionError(project: com.intellij.openapi.project.Project, backend: CodexBackend, error: Throwable) {
        val now = System.currentTimeMillis()
        val last = lastInlineErrorAtMs.get()
        val intervalMs = TimeUnit.SECONDS.toMillis(30)
        if (now - last < intervalMs) return
        if (!lastInlineErrorAtMs.compareAndSet(last, now)) return

        val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        CodexNotifier.warn(
            project,
            "Codex inline completion failed ($backend): ${message.take(240)}. Check Settings/Preferences | Tools | Codex."
        )
    }

    private fun warnMissingApiConfigOnce() {
        if (!warnedMissingApiConfig.compareAndSet(false, true)) return
        CodexNotifier.warn(null, "Codex inline completions require an OpenAI API base URL + API key in Settings/Preferences | Tools | Codex.")
    }

    companion object {
        private const val MAX_SUGGESTION_CHARS = 2000
        private val warnedMissingApiConfig = AtomicBoolean(false)
        private val lastInlineErrorAtMs = AtomicLong(0L)
    }

    private data class PreparedInlineCompletion(
        val project: Project,
        val messages: List<com.codex.phpstorm.client.ChatCompletionMessage>,
        val prefix: String,
        val suffix: String,
        val existingFunctionNames: Set<String>,
        val atClassLevel: Boolean
    )
}
