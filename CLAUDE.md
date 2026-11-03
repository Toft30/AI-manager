# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

AI-Manager is a small Spring Boot 3.4 (Java 17) multi-agent prototype. A "manager" LLM agent (Google Gemini via LangChain4j 1.22.0) receives a task from the user and delegates it to sub-agents exposed as LangChain4j tools. Tasks and their results are persisted with JPA to an in-memory H2 database and shown in a static web dashboard.

Code comments, Javadoc, log messages, enum values and UI text are written in Norwegian Nynorsk — keep new code consistent with that.

## Commands

There is no Maven wrapper (`mvnw`) checked in, and `mvn` is not on PATH on the dev machine; use IntelliJ's bundled Maven (`C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn`).

- Build: `mvn package`
- Run: `mvn spring-boot:run` (serves on http://localhost:8080)
- There are currently no tests (`src/test` does not exist). Once added, run a single test with `mvn test -Dtest=ClassName#method`.

The app fails at startup unless `GEMINI_API_KEY` (or `GOOGLE_API_KEY`) is set in the environment — `AiConfiguration` throws `IllegalStateException` when the key is empty. Never put the key in `application.properties`; that file is tracked in git.

Useful local URLs: dashboard at `/`, H2 console at `/h2-console` (JDBC URL `jdbc:h2:mem:testdb`). Data is lost on restart.

## Architecture

All classes live flat in `org.example` under `src/main/java`.

- **`AiConfiguration`** wires the LLM: builds a `GoogleAiGeminiChatModel` (60 s timeout via `gemini.timeout-seconds`, 3 retries via `gemini.max-retries` — Gemini often returns 503 under high demand; model from `gemini.model`, default `gemini-3.8-flash`; `gemini-2.5-flash` is no longer available to new API users) and creates the `ManagerAgent` bean via `AiServices.builder(...).tools(...)`.
- **`ManagerAgent`** is an interface only — LangChain4j generates the implementation. Its `@SystemMessage` defines the manager's delegation behavior.
- **`CalendarAgent`** talks to the real Google Calendar API (tools: `getCurrentDateTime`, `scheduleMeeting`, `listMeetings`, `deleteMeeting`). Tools return error text instead of throwing, so the LLM can report failures. The Calendar client is created lazily on first tool call; if `tokens/` has no stored grant, it runs the OAuth installed-app flow (opens a browser, callback on port 8888) and blocks that HTTP request until the user logs in. Needs `credentials.json` (OAuth client of type "Desktop app") in the project root — both it and `tokens/` are gitignored. Settings: `google.calendar.*` in `application.properties`.
- **Sub-agents** are Spring `@Component`s with `@Tool`-annotated methods. To add a new sub-agent: create the component, then inject it into `AiConfiguration.managerAgent(...)` and add it to `.tools(...)`. The LLM decides which tool to call based on the `@Tool` description.
- **`TaskController`** exposes `GET /api/task?task=...` (synchronous: saves a `Task` as `MOTTATT` → `PÅGÅANDE`, calls the agent, then stores the result as `FULLFØRT` or `FEILA`) and `GET /api/tasks` (list all).
- **`Task` / `TaskRepository` / `TaskStatus`**: JPA entity + Spring Data repository. `TaskStatus` names are used directly as CSS class names in the dashboard, so renaming an enum value requires updating `static/style.css`/`app.js`.
- **Model chain (`AgentRouter`)**: `TaskController` calls `AgentRouter.ask`, which tries `gemini.model` and then each of `gemini.fallback-models` (default `gemini-3.5-flash,gemini-3.5-flash-lite`) in order. Only the last model retries; the others fail fast. On a 429 the router skips that model until Google's `retryDelay` (the free tier allows only ~20 requests/day per model); on other errors (e.g. 503) it skips it for 2 minutes. The last model is always tried. All agents share one `ChatMemory` (30 messages), which is restored from a snapshot after a failed attempt so the user message isn't duplicated. `POST /api/reset` clears it. `/api/status` reports the model that answered last as `brain`.
- **Dialect dictionary (`DialectMemory`)**: a self-learning dictionary of dialect words (`ORD`), speech-recognition mistakes (`FEILHØYRING`) and facts about the user (`FAKTA`), stored as JSON in `odin.memory-file` (default `minne/ordbok.json`; `minne/` is gitignored because it is personal). It is rendered into the system message as `{{memory}}` on every question, together with `odin.dialect`. It is also a tool component (`learnWord`, `learnMishearing`, `rememberFact`, `forget`) registered on every agent. Weak models like flash-lite sometimes *claim* they saved something without calling the tool, so `TaskController` first runs `learnFromText`, a regex safety net for explicit phrasings («når eg seier X, meiner eg Y», «du høyrde X, eg sa Y», «hugs at …»). `ORD` terms and `FEILHØYRING` meanings are sent to ElevenLabs Scribe v2 as `keyterms` (max 100; only letters, digits, space, `-` and `'` are accepted). `MemoryController` exposes `GET/POST /api/memory` and `DELETE /api/memory/{id}`, used by the "Ordbok" panel in the HUD. When testing with curl from Git Bash on Windows, non-ASCII arguments arrive as `?`, so test non-ASCII input from the browser (`fetch` + `encodeURIComponent`) instead.
- **Subtasks (`/plan`, ported from PR #10)**: `GET /api/task?plan=true` hands the saved `Task` to `TaskOrchestratorService`. `AgentRouter.plan` asks `PlannerAgent` (no chat memory, no tools) for at most `MAX_SUBTASKS` (4) steps as a `TaskDecompositionResult`. Each step becomes a `SubTask` (cascaded from `Task.subTasks`, EAGER so `/api/tasks` includes them) and is sent through `AgentRouter.ask` with its subtask id. Statuses are re-read from `SubTaskRepository` afterwards, because the model may mark a step `FEILA` via `TaskUpdaterTool.updateSubTaskStatus`. With more than one step, a final summary call produces the spoken answer. Cost: 1 + steps (+1) Gemini calls, so normal questions never use it. In the HUD, type `/plan <oppgåve>`; subtasks render under their task in the action log.
- **Structured tool results**: `CalendarAgent` and `TaskUpdaterTool` return `ToolResponse` JSON (`{"status": "fullført"|"feila", "partialResult": …}`). The system message tells the manager to evaluate it and retry a failed tool call **once** with corrected arguments (PR #10 said "until it's good", which burns the free-tier quota). `CalendarAgent.checkAvailability` lists overlapping, non-transparent events in the slot.
- **Current time in the prompt**: `ManagerAgent.delegateTask(task, now)` fills `{{now}}` in the system message from `CalendarAgent.getCurrentDateTime()`, so relative dates need no extra tool round trip (each tool call costs a Gemini request).
- **Voice (ODIN)**: `VoiceService` calls ElevenLabs — TTS `POST /v1/text-to-speech/{voice}` and STT `POST /v1/speech-to-text` (Scribe, `language_code=nor`). `VoiceController` exposes `POST /api/speak` (mp3), `POST /api/listen` (multipart `audio`), `GET /api/status`. The key stays server-side. Without `ELEVENLABS_API_KEY` the frontend falls back to the browser's `speechSynthesis`/`SpeechRecognition`. The `ManagerAgent` system message asks for short spoken replies with no markdown.
- **Secrets in `.env`**: `application.properties` imports `optional:file:.env[.properties]` (gitignored; see `.env.example`). It holds `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`, `ELEVENLABS_MODEL`. Never print or commit it.
- **Frontend** (`src/main/resources/static/`): plain HTML/CSS/JS, no build step. A ODIN-style HUD: an "Aktiver ODIN" click unlocks audio and plays a greeting; the ask bar calls `/api/task` and speaks the result; MIC is push-to-talk; LIVE is hands-free with a calibrated voice-activity detector (tune `SILENCE_MS`, `MIN_SPEECH_MS`, `MAX_SPEECH_MS` in `app.js`), paused while ODIN thinks or speaks. `window.odin.lastSpeech` records the last playback (bytes, duration, start/end) for verification. Polls `/api/tasks` every 3 s and `/api/status` every 5 s.

## Repo notes

- `Test/` is a gitignored local clone of the repo — ignore it when searching or editing.
