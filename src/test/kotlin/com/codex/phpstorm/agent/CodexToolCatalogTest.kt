package com.codex.phpstorm.agent

import com.codex.phpstorm.settings.CodexSettingsState
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexToolCatalogTest {

    @Test
    fun `tools are empty when agent mode disabled`() {
        val settings = CodexSettingsState.State().apply {
            agentModeEnabled = false
            allowFileRead = true
            allowFileWrite = true
            allowCommandExecution = true
        }

        val tools = CodexToolCatalog.toolsFor(settings)
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `read tools are included when enabled`() {
        val settings = CodexSettingsState.State().apply {
            agentModeEnabled = true
            allowFileRead = true
            allowFileWrite = false
            allowCommandExecution = false
        }

        val names = CodexToolCatalog.toolsFor(settings).map { it.function.name }.toSet()
        assertTrue(names.contains("list_files"))
        assertTrue(names.contains("read_file"))
        assertFalse(names.contains("write_file"))
        assertFalse(names.contains("delete_file"))
        assertFalse(names.contains("run_command"))
    }

    @Test
    fun `write tools are included when enabled`() {
        val settings = CodexSettingsState.State().apply {
            agentModeEnabled = true
            allowFileRead = false
            allowFileWrite = true
            allowCommandExecution = false
        }

        val names = CodexToolCatalog.toolsFor(settings).map { it.function.name }.toSet()
        assertFalse(names.contains("list_files"))
        assertFalse(names.contains("read_file"))
        assertTrue(names.contains("write_file"))
        assertTrue(names.contains("delete_file"))
        assertFalse(names.contains("run_command"))
    }

    @Test
    fun `run command tool is included when enabled`() {
        val settings = CodexSettingsState.State().apply {
            agentModeEnabled = true
            allowFileRead = false
            allowFileWrite = false
            allowCommandExecution = true
        }

        val names = CodexToolCatalog.toolsFor(settings).map { it.function.name }.toSet()
        assertTrue(names.contains("run_command"))
        assertEquals(1, names.size)
    }

    @Test
    fun `schemas have required fields`() {
        val settings = CodexSettingsState.State().apply {
            agentModeEnabled = true
            allowFileRead = true
            allowFileWrite = true
            allowCommandExecution = true
        }

        val tools = CodexToolCatalog.toolsFor(settings)
        val byName = tools.associateBy { it.function.name }

        val readSchema = byName.getValue("read_file").function.parameters.jsonObject
        val readRequired = readSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue(readRequired.contains("path"))

        val writeSchema = byName.getValue("write_file").function.parameters.jsonObject
        val writeRequired = writeSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue(writeRequired.contains("path"))
        assertTrue(writeRequired.contains("content"))

        val runSchema = byName.getValue("run_command").function.parameters.jsonObject
        val runRequired = runSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue(runRequired.contains("command"))
    }
}

