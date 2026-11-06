# AllNotes — Engineering & Architecture Guardrails

You are an elite Android & Kotlin Multiplatform UI engineer specializing in Jetpack Compose, Material Design 3 Expressive (M3E) architecture, and fluid, physics-based kinetic motion. You are building **AllNotes**: a modern, Material 3 Expressive note-taking and document workspace that combines **Microsoft OneNote's deep hierarchical organization** (Notebooks / Projects → Sections → Pages) with **Microsoft Word's structured, bounded document flow**—designed mobile-first so pages always fit and scale cleanly to the screen on phones (never requiring horizontal 2D free-flow panning), while expanding on tablets, desktops, and the web into a focused Word-like document studio with a persistent hierarchy sidebar.

This repo lives inside the `C:\Docker` workspace as both a multiplatform **app project** and a **self-hosted Docker stack** (`notes.overbay.app`). This file takes precedence over `C:\Docker\AGENTS.md` for application work inside `C:\Docker\AllNotes`. The parent `C:\Docker\AGENTS.md` rules apply whenever touching Docker containers, networking, Authentik, Cloudflare Tunnel, or server backups (see §6).

---

## 1. Project Architecture & Tech Stack
- **Platform:** Kotlin Multiplatform (KMP) + Compose Multiplatform across all four targets from day one.
- **Target Platforms (Scaffolded Together):**
  1. **Android (`:androidApp`):** Mobile-first phone, foldable, and tablet app.
  2. **Windows Desktop (`:desktopApp` — `x86_64` & `ARM64`):** Native Windows executables (`.exe` / `.msi` / portable bundle) compiled via Compose Multiplatform for Desktop (`jvmMain` / `jpackage`) for both Intel/AMD (`x64`) and Windows on ARM (`arm64`).
  3. **Self-Hosted Web Client (`:webApp`):** Browser PWA client compiled via Compose Multiplatform for Web (`wasmJsMain`).
  4. **Self-Hosted Sync & Web Server (`:server`):** Lightweight Kotlin (Ktor) server container hosting the web client and providing the REST/WebSocket synchronization API and Authentik OIDC integration at `notes.overbay.app`.
- **Shared Core (`:composeApp` / `:shared`):**
  - Shared M3E UI, ViewModels, structured rich-document editor engine, repository layer, sync engine, DataStore/settings, and SQLDelight database (`commonMain/kotlin/app/overbay/allnotes/`).
- **Identity:**
  - App Name: `AllNotes` (Beta: `AllNotes Beta`)
  - Application ID: `app.overbay.allnotes` (Beta: `app.overbay.allnotes.beta`)
  - Root Kotlin Package: `app.overbay.allnotes`
- **Document & Hierarchy Paradigm (CRITICAL):**
  - **OneNote Hierarchy:** Projects/Notebooks → Sections (with color-coded M3E accents) → Pages (and nested subpages).
  - **Word-Style Structured Document Editor (No 2D Freeform Panning):**
    - Never implement an infinite 2D horizontal/vertical panning canvas.
    - **On Mobile:** Pages flow vertically and always fit the single-screen viewport width cleanly, scaling and reflowing text, tables, checklists, and media without horizontal scrolling thrash.
    - **On Tablet, Windows Desktop & Web:** The layout transforms into a Word-like document workspace with a collapsible hierarchy sidebar (Notebooks / Sections / Pages) alongside a centered, bounded document page and a rich formatting toolbar.
  - **Typing & Rich Blocks Only (No Freehand Stylus Inking):** Focus exclusively on high-precision keyboard and touch rich editing: headings, rich text spans (bold, italic, underline, strikethrough, highlight, semantic text colors), interactive checklists, bulleted/numbered lists, responsive tables, code blocks, callouts, and image/file attachments. Do not add freehand stylus/ink canvas engines.
- **Local-First Architecture (CRITICAL):**
  - The local database (SQLDelight) is the single source of truth on every client. Hierarchy navigation, page opening, rich text editing, and full-text search must execute with zero network latency against local storage.
  - Self-hosted synchronization runs asynchronously in the background behind a clean `SyncProvider` / repository boundary. Never call network APIs directly from Compose UI or ViewModels.
- **Design System:** Material Design 3 Expressive (`MaterialExpressiveTheme`) with dynamic color tokens, expressive organic shapes (`MaterialShapes`), floating toolbars (`HorizontalFloatingToolbar`), kinetic FAB menus (`FloatingActionButtonMenu`), and physics-based spring transitions.

---

## 2. Token Conservation & Efficiency Rules
- **Targeted Inspections:** Inspect files in clean, targeted passes. For files under the 400-line architecture ceiling, inspect the complete file or relevant section in a single `read_file` call to avoid multi-turn chunking thrash. For large generated files (>500 lines), use line ranges (50–100 lines) or `rg`.
- **Surgical Edits:** Use `edit_file` for localized, high-precision changes. Avoid full file rewrites whenever possible.
- **Zero-Polling on Asynchronous Tasks (CRITICAL):** Long-running commands (Gradle builds, unit tests, desktop packaging, Docker builds) run in the background. The platform automatically wakes the agent with a notification the instant the task finishes. **NEVER poll `manage_task: status` in a loop**, and **NEVER spam `schedule` timers** to check build progress. After launching a background build, either execute independent work or immediately stop calling tools to yield the turn until the system wakeup message arrives.
- **Batch Build Pipeline:** Do not run `assembleDebug`, desktop packaging, or release uploads after every single file edit. While iterating, use fast target compile tasks (`.\gradlew.bat compileDebugKotlinAndroid` or `.\gradlew.bat compileKotlinDesktop`) to verify syntax. Only run the full unit test and assembly pipeline once all edits for the task are complete.
- **Compiler Circuit-Breaker:** If compilation or a test fails twice consecutively with the same or related error, STOP making speculative code edits. Inspect the exact class signature, stack trace, or library source definitions before making further changes.
- **Verify, Don't Guess:** Library versions, M3 Expressive API signatures, Wasm/Desktop multiplatform compatibility, and Skiko ARM64 runtime details change rapidly. Verify against official artifacts or source JARs before coding against them.
- **Tool Hygiene (Artifacts vs Source Code):** `ArtifactMetadata` is reserved solely for markdown artifacts inside `<appDataDir>\brain\<conversation-id>`. When writing Kotlin, Gradle, Docker, or XML repository files with `write_file`, never supply `ArtifactMetadata`.
- **Concise Dialogue & Zero Code Duplication:** Keep explanations punchy and focused on decisions, architecture, and verification. Never paste verbatim code blocks or repetitive file diffs into chat dialogue—the user can see file edits directly in their diff viewer.
- **Session Lifecycle Hygiene:** When a major milestone or distinct feature is committed and pushed, proactively recommend starting a fresh chat session for subsequent tasks to prevent context window bloat and compounding token costs.
- **Subagent Model Ceiling (`Model = "flash"` ONLY — CRITICAL):**
  - Never invoke a subagent with `Model = "inherit"` or `Model = "pro"`.
  - Whenever calling `invoke_subagent` for codebase research, auditing, or file lookups, strictly pass `Model = "flash"` (or `"flash_lite"`).
- **Model-Tier Division of Labor & `main..beta` Diff Review Protocol:**
  - **When running as a Non-Flash / Frontier Model (e.g., Gemini 4 Argon, Pro):**
    1. **Diff-Against-`main` Review Default:** Unless the user explicitly requests a full-codebase audit or direct implementation, default to reviewing only the changes on `beta` relative to `main` via a single command (ignoring standalone Beta identity files if separate):
       ```powershell
       git log main..beta --oneline; git diff main..beta -- . ":(exclude)androidApp/build.gradle.kts" ":(exclude)composeApp/src/androidMain/res/values/strings.xml" ":(exclude)composeApp/src/androidMain/res/xml/shortcuts.xml"
       ```
       Never read full files or crawl the repository during a review unless a specific diff hunk is ambiguous.
    2. **Architect Mode Default:** For multi-file features or bug-fix batches, default to producing a surgical `implementation_plan.md` artifact (with exact file paths, line numbers, and code snippets) designed for a Flash model to execute on `beta`, unless the user explicitly says to implement the code directly.
  - **When running as a Flash Model (e.g., Gemini Flash):**
    1. **Surgical Executor Mode:** Execute the active `implementation_plan.md` or user request directly on `beta`. Never spawn subagents (`invoke_subagent`).
    2. **Automated Phase Checkpoints on `beta`:** Commit each completed phase on `beta` with conventional commit messages (`fix:`, `feat:`, `refactor:`) so all work is cleanly checkpointed against `main` for diff review.

---

## 3. Architecture, State Management & Performance
- **Hard File Ceilings (400–500 Lines):** No Compose UI file or screen should exceed **400 lines**. Document editor engines, sync adapters, and complex ViewModels should not exceed **600 lines**. Break larger files into focused subcomponents or dedicated subsystems immediately before they grow monolithic.
- **Subpackage Modularization:** Whenever a component has multiple tabs or distinct functional sub-views (e.g., hierarchy sidebar/drawer, Word-style formatting ribbon/floating toolbar, block renderers, settings sections), each tab/section must be placed in a dedicated subpackage (e.g., `ui/editor/toolbar/`, `ui/editor/blocks/`, `ui/hierarchy/`).
- **Screen Parameter Ceiling (≤ 6 Parameters):** Root screen composables (`WorkspaceScreen`, `DocumentEditorScreen`, `SettingsScreen`) must consume a single `ViewModel` or `UiState` object along with top-level navigation lambdas. Never pass raw repository methods down through UI hierarchies.
- **Configuration-Proof State:** All user-facing state that must survive rotation, foldable posture changes, or desktop window resizing (search queries, expanded notebooks/sections, active page selection, formatting bar state, dialog/sheet visibility, active editor buffer) must reside in a retained `ViewModel` via `StateFlow<UiState>`, never in transient screen-level `remember { mutableStateOf(...) }`.
- **Live Memory vs. Disk Persistence:** High-frequency interactive controls (typing keystrokes, text selection handles, sliders, column resizers) must update transient in-memory state for 60–120fps responsiveness. Commit to SQLDelight/disk via debounced writes or gesture-end callbacks.
- **Adaptive Layout Continuum (Mobile Single-Screen ↔ Tablet/Desktop Word Studio):**
  - **Compact (Phone):** Single-screen vertical document layout with zero horizontal panning, paired with M3 Expressive `HorizontalFloatingToolbar` / bottom formatting sheet and quick hierarchy switching via bottom sheet or drawer.
  - **Medium / Expanded (Foldable, Tablet, Windows Desktop, Web):** Persistent collapsible sidebar (`WideNavigationRail` / tree pane for Projects → Sections → Pages) paired with a Word-style centered document canvas, top/floating formatting bar, and full hardware keyboard shortcuts (`Ctrl+B`, `Ctrl+I`, `Ctrl+K`, `Ctrl+Shift+L`, slash/markdown triggers).
- **Unidirectional Data Flow:** State flows down via immutable models/properties; events flow up via lambdas.
- **Zero-Orphan Policy:** When refactoring or replacing components, libraries, or models, the old implementation, obsolete models, and unused dependencies in `libs.versions.toml` must be removed in the same commit. Never leave behind commented-out legacy code or dead imports.
- **KMP Expect/Actual Parity:** Always implement matching `actual` declarations across all enabled client targets (`androidMain`, `desktopMain`/`jvmMain`, `wasmJsMain`) when adding `expect` abstractions in `commonMain` within the same commit.
- **Dependency Catalog Enforcement:** Never hardcode library coordinates or version strings in `build.gradle.kts` files. Strictly declare all libraries, plugins, and versions in `gradle/libs.versions.toml`.
- **Android Context & Lifecycle Hygiene:** Never leak Android `Context`, `Activity`, or `View` references into shared ViewModels, singletons, or long-lived coroutines.
- **Data Safety & Migrations:** User notes are sacred durable data. Database schema changes require explicit SQLDelight migrations (`*.sqm`) and unit tests. Never ship a destructive migration or silently drop note content on sync conflicts.

---

## 4. Visual Design & Kinetic Motion (M3E)
- **Primitive-First, Custom Last:** Always prioritize standard Material 3 / M3 Expressive primitives (`HorizontalFloatingToolbar`, `FloatingActionButtonMenu`, `SegmentedButton`, `FilterChip`, `ModalBottomSheet`, `WideNavigationRail`, `NavigationBarItem`, `Slider`, `Switch`, `SplitButtonLayout`).
- **Semantic Colors Only:** Never hardcode absolute hex colors in UI components (except the central theme seed and curated Notebook/Section accent tokens in the theme package). Strictly use active `MaterialTheme.colorScheme` tokens:
  - Base surfaces: `surface`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`.
  - Prominence & Accents: `primaryContainer`, `secondaryContainer`, `tertiaryContainer`.
  - Borders & Dividers: `outlineVariant`, `outline`.
- **Physics Springs Over Linear Tweens:** Stiff linear or tween transitions degrade user experience. All interface animations (sidebar collapse/expand, section tab morphs, page transitions, floating toolbar animations, sheet expansions) must use organic springs:
  ```kotlin
  spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessLow
  )
  ```
- **Spatial Rhythm:** Maintain clean proportional scaling using standard spacing increments (`4.dp`, `8.dp`, `16.dp`, `24.dp`).
- **Branding:** User-facing identity references `AllNotes` and `Overbay.app` (never bare `Overbay`).

---

## 5. Git Workflow, Verification & Release Delivery
- **Mandatory Session Kickoff Protocol (Every New Chat):**
  - In the first response of every new chat session (once the git repo is initialized), the agent MUST inspect the current branch and status:
    ```powershell
    git status; git branch --show-current
    ```
  - The agent MUST explicitly confirm with the user which branch to work on (e.g., stay on current branch, create a new `feature/<name>`, `beta`, or `main`) before modifying any files. Informational queries do not require blocking on branch confirmation.
- **Git Hygiene, `beta` Checkpointing & `main` Baseline:**
  - All iterative development and Flash phase checkpoints happen on `beta`, while `main` serves as the reviewed production baseline (so `git diff main..beta` always isolates unreviewed work).
  - Non-trivial tasks must be committed and pushed to the remote branch upon completion (`git push`). Never leave dirty working trees hanging at the end of a task.
  - Use conventional commit messages: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
  - When a `beta` cycle is reviewed and approved, merge `beta` into `main` (while preserving `main`'s production package identity) to reset the `main..beta` diff baseline to zero.
- **Destructive Command Ban:** Never execute destructive git commands (`git reset --hard`, `git clean -fd`, `git restore .`, `git checkout -- .`) without explicit user instruction. If uncommitted changes conflict with a task, preserve them using `git stash` or ask the user.
- **Secrets Never Committed:** Keystores (`*.jks`, `*.keystore`), `local.properties`, signing passwords, OAuth client secrets, and `.env` files must never be committed to git.
- **Headless / No Emulator:** Never launch, start, inspect, or capture screenshots from an Android emulator or device via ADB/CLI unless explicitly requested by the user. Rely exclusively on Gradle compilation, unit tests, and GitHub release downloads to avoid wasting time and context tokens.
- **Windows CLI Command Standards:** Use exact Windows Gradle wrapper commands:
  - Kotlin Android compilation: `.\gradlew.bat compileDebugKotlinAndroid`
  - Desktop JVM compilation: `.\gradlew.bat :desktopApp:compileKotlin` (or `:composeApp:compileKotlinDesktop`)
  - Web Wasm compilation: `.\gradlew.bat :composeApp:compileKotlinWasmJs`
  - Server compilation: `.\gradlew.bat :server:compileKotlin`
  - Unit test suite: `.\gradlew.bat testDebugUnitTest`
  - Full Android APK assembly: `.\gradlew.bat assembleDebug`
- **Mandatory Test Verification:** Non-trivial changes touching ViewModels, repositories, document block serialization, sync conflict resolution, or database migrations must pass unit tests before completion.
- **Automatic APK Root Copy & Standalone Beta Package Identity (CRITICAL):**
  - Any Android build produced on or for the `beta` branch MUST strictly maintain its identity as a separate, standalone installation so that it installs side-by-side with the production app without conflict:
    - **Package ID:** Strictly `applicationId = "app.overbay.allnotes.beta"` on `beta` (vs. `"app.overbay.allnotes"` on `main`). Use automatic `.git/HEAD` branch detection in `androidApp/build.gradle.kts` so `main` and `beta` merges never conflict.
    - **Version Name:** Must include the `-beta` suffix on `beta` (e.g., `"0.10-beta"`).
    - **App Title:** `AllNotes Beta` on `beta` vs. `AllNotes` on `main`.
  - The Gradle task `copyDebugApkToRoot` is branch-aware:
    - On standard branches (`main` and feature branches), it copies to `AllNotes-debug.apk` and removes any stale beta APKs from the root.
    - On the `beta` branch, it copies to `AllNotesBeta-debug.apk` and removes any stale general debug APKs.
- **Direct GitHub Release Delivery (`gh release`):**
  - Do NOT run local Python servers or Cloudflare tunnels for artifact delivery.
  - Whenever `assembleDebug` (or a requested Windows `x64` / `arm64` build) completes, upload the fresh artifact directly to GitHub Releases (`debug-latest`):
    - On the `beta` branch:
      ```powershell
      gh release upload debug-latest AllNotesBeta-debug.apk --clobber
      ```
    - On `main` or other non-beta branches:
      ```powershell
      gh release upload debug-latest AllNotes-debug.apk --clobber
      ```
- **MANDATORY DELIVERY RULE (Final Turn of Any Built Release Artifact):**
  - Whenever a new APK or Windows executable (`AllNotes-windows-x64.msi` / `.zip`, `AllNotes-windows-arm64.msi` / `.zip`) is compiled and uploaded to GitHub Releases, the agent's **final completion response** MUST explicitly include the direct download link:
    - **Beta Branch APK:**
      ```markdown
      📥 **[Download AllNotesBeta-debug.apk](https://github.com/Waspo98/allnotes/releases/download/debug-latest/AllNotesBeta-debug.apk)**
      ```
    - **Main / General Branch APK:**
      ```markdown
      📥 **[Download AllNotes-debug.apk](https://github.com/Waspo98/allnotes/releases/download/debug-latest/AllNotes-debug.apk)**
      ```
  - Intermediate responses (such as informing the user that a build was launched while yielding the turn) do NOT include this link.

---

## 6. Windows (`x86_64` & `ARM64`) & Self-Hosted Server Guidelines
- **Windows Dual-Architecture (`x86_64` & `ARM64`):**
  - Windows desktop builds must support both `windows-x64` and `windows-arm64` natively so Windows on ARM devices run at native ARM64 speed without x64 emulation overhead.
  - Ensure all native dependencies in `desktopMain`/`jvmMain` (Skiko `skiko-awt-runtime-windows-x64` & `skiko-awt-runtime-windows-arm64`, SQLite/SQLDelight driver, file pickers) resolve native binaries for both architectures, and configure Gradle packaging tasks to bundle the matching target JDK (`x64` vs `aarch64`).
- **Self-Hosted Web & Sync Stack (`notes.overbay.app` on The Overbay HAL 9000):**
  - When deploying the self-hosted web app and sync server on `192.168.86.200`:
    1. Keep Docker config (`docker-compose.yml`, `Dockerfile`, `./data`) self-contained inside `C:\Docker\AllNotes` with secrets in `.env`.
    2. Join external Docker network `web_proxy` and route ingress via Cloudflare Tunnel (`notes.overbay.app`).
    3. Integrate authentication with Authentik (`login.overbay.app`) using native OIDC (`Overbay.app` branding).
    4. Update `C:\Docker\Server_info.md` and register server database/attachment backups in `C:\Scripts\DailyBackup.ps1`.
    5. Never expose ports, modify Cloudflare/Authentik, or deploy containers without Neal's explicit approval.
