# Codex Chat for PhpStorm

PhpStorm plugin that brings Codex's chat workflow into the IDE. It mirrors the Visual Studio extension by adding a docked chat panel, quick actions for sending selections, and configurable backend settings.

## Features
- Tool window (`Codex Chat`) that keeps a running conversation with the assistant.
- Optional inclusion of the active editor selection as context.
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
   - System prompt.
   - (Optional) Enable agent mode and permissions for file/command access.
2. Open the `Codex Chat` tool window (Tools menu or the tool window stripe).
3. Type a message or right–click a selection and choose *Ask Codex About Selection*.

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
- The default API base is `http://localhost:8700/v1`; adjust to match your Codex deployment.
- Requests follow the OpenAI chat-completions schema; when agent mode is enabled the plugin uses `tools` / `tool_calls`.
- Codex CLI mode runs `codex exec --json` in the project directory and parses the JSONL events to extract the assistant message.
