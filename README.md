# Codex CLI/Chat/Copilot for PhpStorm

PhpStorm plugin that brings Codex's CLI, chat workflow, and Copilot-style inline completions into the IDE. It mirrors the Visual Studio extension by adding a docked chat panel, quick actions for sending selections, an embedded Codex CLI terminal, and configurable backend settings.

## Licentie
Deze plugin is gelicentieerd onder de Apache License, Version 2.0. Zie het LICENSE-bestand voor de volledige tekst.

## Disclaimer
Deze plugin is niet gelieerd aan of ondersteund door JetBrains of OpenAI. Namen en merken worden alleen ter identificatie gebruikt.

## Features
- Tool window (`Codex Chat`) that keeps a running conversation with the assistant.
- Tool window (`Codex CLI`) that runs the interactive `codex` CLI inside an embedded terminal.
- Copilot-style inline code completions (ghost text) using the selected backend (with a separate optional model).
- Backend selector in the chat tool window (switch between OpenAI API and Codex CLI).
- Optional inclusion of the active editor selection as context.
- Model dropdowns (OpenAI model list can be fetched live from OpenAI).
- Optional notifications for new OpenAI models and plugin updates.
- Settings page for selecting a backend:
  - OpenAI API (`/v1/chat/completions`)
  - Local Codex CLI (`codex exec`)
  - Plus API/CLI-specific settings (URL/key/model, CLI path/args).
- Optional “agent mode” with tool calls (read/write project files, run commands) gated by approvals.
- Editor popup action: *Ask Codex About Selection*.

## Getting Started
1. Configure Codex connection in `Settings/Preferences | Tools | Codex`:
   - `Backend`:
     - `OpenAI API`: set API base URL, key, model, temperature.
     - `Codex CLI`: set `Codex CLI path` (default `codex`) and optional extra args.
   - (Optional) Enable `Copilot-style inline completions` to get ghost-text suggestions in the editor (use `Tab` to accept).
   - (Optional) Set `Inline completion model` if you want it to differ from the main chat model.
   - Click `Refresh models` after setting your API key to load the OpenAI model list.
   - System prompt.
   - (Optional) Enable agent mode and permissions for file/command access.
2. Open the `Codex Chat` tool window (Tools menu or the tool window stripe).
3. Open the `Codex CLI` tool window to run the interactive CLI in an embedded terminal (it is placed near the built-in Terminal tool window by default).
4. Type a message or right–click a selection and choose *Ask Codex About Selection*.

### Agent Mode (Approvals)
When `Enable agent mode` is turned on, the plugin sends OpenAI-style `tools` definitions. Codex can then request actions like:
- `read_file` / `list_files`
- `write_file` / `delete_file`
- `run_command` (only if enabled in settings)

Each tool call prompts you for approval before anything is executed. You can also toggle `Auto-approve actions (this session)` in the chat tool window.

## Building
Requirements: Java 17 (Gradle will download a toolchain automatically if missing).

```bash
./gradlew build
```

The packaged plugin is written to `build/distributions/`. Install it in PhpStorm via `Settings | Plugins | Install Plugin from Disk…`.

## Notes
- The default API base is `https://api.openai.com/v1`; if you have a local proxy/backend, point it to your `/v1` base URL instead.
- Requests follow the OpenAI chat-completions schema; when agent mode is enabled the plugin uses `tools` / `tool_calls`.
- Codex CLI mode runs `codex exec --json` in the project directory and parses the JSONL events to extract the assistant message.
