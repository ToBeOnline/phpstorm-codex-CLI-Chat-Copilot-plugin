package com.codex.phpstorm.inline

import com.codex.phpstorm.settings.CodexSettingsState
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.Disposable
import com.intellij.ide.DataManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class CodexInlineCompletionAutoTriggerService : Disposable {

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(AutoTriggerDocumentListener(), this)
    }

    override fun dispose() = Unit

    private class AutoTriggerDocumentListener : BulkAwareDocumentListener {
        override fun documentChangedNonBulk(event: DocumentEvent) {
            val settings = CodexSettingsState.getInstance().state
            if (!settings.inlineCompletionEnabled) return

            if (event.oldLength != 0) return
            if (event.newLength <= 0) return

            val inserted = event.newFragment.toString()
            if (!CodexInlineCompletionUtils.shouldAutoTriggerOnInsertedText(inserted)) return

            for (editor in EditorFactory.getInstance().getEditors(event.document)) {
                if (!isFocusedMainEditor(editor)) continue
                schedule(editor)
            }
        }

        private fun schedule(editor: Editor) {
            val alarm = editor.getUserData(AUTO_TRIGGER_ALARM_KEY) ?: Alarm(Alarm.ThreadToUse.SWING_THREAD).also {
                editor.putUserData(AUTO_TRIGGER_ALARM_KEY, it)
            }

            alarm.cancelAllRequests()
            alarm.addRequest({ trigger(editor) }, AUTO_TRIGGER_IDLE_MS)
        }

        private fun trigger(editor: Editor) {
            val settings = CodexSettingsState.getInstance().state
            if (!settings.inlineCompletionEnabled) return

            if (editor.isDisposed) return
            val project = editor.project ?: return
            if (project.isDisposed) return

            if (editor.editorKind != EditorKind.MAIN_EDITOR) return
            if (editor.caretModel.caretCount != 1) return
            if (editor.selectionModel.hasSelection()) return

            val handler = InlineCompletion.getHandlerOrNull(editor) ?: return

            val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
            val caret = editor.caretModel.currentCaret
            handler.invoke(InlineCompletionEvent.DirectCall(editor, caret, dataContext))
        }

        private fun isFocusedMainEditor(editor: Editor): Boolean {
            if (editor.isDisposed) return false
            if (editor.editorKind != EditorKind.MAIN_EDITOR) return false

            val project = editor.project ?: return false
            if (project.isDisposed) return false

            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
            return SwingUtilities.isDescendingFrom(focusOwner, editor.contentComponent)
        }
    }

    companion object {
        private const val AUTO_TRIGGER_IDLE_MS = 450
        private val AUTO_TRIGGER_ALARM_KEY = Key.create<Alarm>("codex.inline.autoTrigger.alarm")
    }
}
