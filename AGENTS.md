# AGENTS Workflow

This document defines the working workflow for contributors and coding agents in this repository.

## 1) Core Development Principles

- Keep code concise, minimal, and clean without losing functionality or safety.
- Prefer TDD by default: write/adjust tests first, then implement.
- Fully document public methods/functions/types with KDoc.
- Preserve architecture boundaries and avoid duplicating responsibilities across layers.

## 2) Architecture Boundaries (Current Project Rules)

- `storage` keeps canonical logical lines only.
- Wrapping/reflow is **not** persisted in storage; it is computed for rendering/viewport mapping.
- `manager` owns runtime state: screen size, cursor, attributes, viewport behavior, editing orchestration.
- `editor` is the application entry point that delegates to manager and renderer through contracts.
- `renderer` consumes `RenderFrame` and does not mutate storage/manager state.
- `ui` calls contract APIs; domain invariants are enforced by buffer/editor/manager, not by UI-only hacks.

## 3) Delivery Workflow Per Change

1. Read relevant requirements in `README.md` and current task documentation.
2. Update/create implementation notes when behavior decisions change.
3. Add or update tests first for the intended behavior.
4. Implement the smallest correct change.
5. Run quality gate:
   - `./gradlew format lint test`
6. Update task checkboxes/docs when scope is complete.
7. Provide a short iteration summary including:
   - what changed,
   - why the design/code decision was made,
   - test/lint/format result.

## 4) Testing Expectations

- Keep behavior covered with focused unit tests around manager/editor/storage/render boundaries.
- Add regression tests for discovered bugs before or alongside fixes.
- Target around 80% coverage over time (pragmatic, not artificial).

## 5) Documentation Rules

- Public APIs must have KDoc with invariants and exception behavior.
- Task documentation is the source of truth for progress and deferred items.
- If assumptions change, record them in docs immediately.

## 6) Non-Goals During Routine Iterations

- Do not refactor unrelated modules.
- Do not revert unrelated user changes.
- Do not introduce new dependencies unless necessary and justified.

## 7) Project Setup and Run

- Prerequisites:
  - JDK 25 available on `PATH`.
  - Unix-like shell (`zsh`/`bash`) for `./gradlew`.
- Initial setup:
  - `./gradlew --version`
  - `./gradlew format lint test`
- Run tests during development:
  - `./gradlew test`
  - `./gradlew format lint test` before finalizing changes.
- Run the app:
  - Start `terminalbuffer.MainKt` from the IDE run configuration (entry point: `src/main/kotlin/terminalbuffer/Main.kt`).
  - The app prompts for width/height, then starts the command-driven terminal UI.
