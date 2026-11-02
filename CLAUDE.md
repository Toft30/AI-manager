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
- **Frontend** (`src/main/resources/static/`): plain HTML/CSS/JS, no build step. `app.js` polls `/api/tasks` every 3 seconds and submits new tasks via `/api/task`.

## Repo notes

- `Test/` is a gitignored local clone of the repo — ignore it when searching or editing.
