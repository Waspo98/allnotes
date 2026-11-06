# AllNotes — Decision Log

Last updated: 2026-11-06. Update this file whenever an architectural decision changes or an open item is resolved.

## Decided

| Area | Decision |
| --- | --- |
| Product | **AllNotes**: A modern Material 3 Expressive note-taking and document workspace combining Microsoft OneNote's deep hierarchy (`Notebooks / Projects → Sections → Pages & Subpages`) with Microsoft Word's structured, bounded document flow. |
| Mobile-First & Adaptive UX | **No 2D free-flow panning.** On mobile, pages fit the single-screen viewport width and scale/reflow cleanly. On tablets, Windows desktops, and Web, the layout expands into a Word-like document workspace with a collapsible hierarchy sidebar alongside a centered, bounded document canvas and formatting bar. |
| Content Scope | **Typing & Rich Blocks Only.** Focused on rich text formatting, headings, checklists, bulleted/numbered lists, responsive tables, code blocks, callouts, and image/file attachments. No freehand stylus/ink canvas. |
| Target Platforms | All four targets scaffolded together in a unified Kotlin Multiplatform repository: **Android** (`:androidApp`), **Windows `x86_64` & `ARM64`** (`:desktopApp`), **Self-Hosted Web PWA** (`:webApp` / `wasmJs`), and **Self-Hosted Sync Server** (`:server`). |
| Identity | App Name: `AllNotes` (Beta: `AllNotes Beta`). Application ID: `app.overbay.allnotes` (Beta: `app.overbay.allnotes.beta`). Root Kotlin package: `app.overbay.allnotes`. |
| Beta Build Detection | Automatic branch detection via `.git/HEAD` (`ref: refs/heads/beta`) with property/env overrides (`-PisBeta=true` or `ALLNOTES_IS_BETA=true`). Produces `AllNotesBeta-debug.apk` on `beta` and `AllNotes-debug.apk` on `main`, eliminating `main`↔`beta` merge conflicts. |
| Local-First & Sync | Local SQLDelight database on clients is the single source of truth for zero-latency navigation, editing, and search. Background sync connects via `SyncProvider` to the self-hosted Ktor server (`notes.overbay.app`) behind Cloudflare Tunnel and Authentik OIDC. |
| Design System | Material Design 3 Expressive (`MaterialExpressiveTheme`) via Compose Multiplatform `1.12.1` + `org.jetbrains.compose.material3:material3:1.12.0-alpha03`, organic physics springs (`Spring.DampingRatioLowBouncy`, `Spring.StiffnessLow`), `HorizontalFloatingToolbar`, `FloatingActionButtonMenu`, and `WideNavigationRail`. |
| Windows Dual-Arch (`x64` & `ARM64`) | Compose Multiplatform Desktop (`jvmMain`) configured with explicit `windows-x64` and `windows-arm64` runtime support (Skiko + SQLite JDBC + target JDK toolchains) so Windows on ARM runs natively without x64 emulation. |
