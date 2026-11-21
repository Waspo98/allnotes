# AllNotes

AllNotes is a local-first note-taking app for Android, Windows, and the web, paired with an optional self-hosted sync server. Notes are organized into notebooks, color-coded sections, pages, and nested subpages. Pages use a vertical single-column layout so they fit phone screens without horizontal panning, and open alongside a collapsible sidebar on tablets and desktops.

## Features

- **Notebook hierarchy:** Group notes into notebooks, color-coded sections (with pinning and custom ordering), pages, and nested subpages.
- **Document blocks:** H1-H3 headings, rich text paragraphs, checklists with completion progress, bulleted and numbered lists, callouts (`Note`, `Tip`, `Key Point`), syntax-highlighted code blocks with copy buttons, dividers, and file or image attachments (up to 2 MB each, with zoomable image previews).
- **Reflowing tables:** Edit tables as a grid on tablets, desktops, and the web. On phone-width screens, tables with three or more columns stack into vertical cards.
- **Inline formatting & slash commands:** Apply bold, italic, underline, strikethrough, highlight, inline code, accent colors, and links. Type `/` inside a paragraph to open the block picker.
- **Markdown import & export:** Import and export pages as GitHub-Flavored Markdown, covering headings, lists, checklists, tables, callouts, and fenced code blocks.
- **Search & quick jump:** Search across notebooks, sections, and pages, or jump to any page with `Ctrl+P`.
- **Local storage & sync:** Android and Windows store data locally in SQLite (SQLDelight); the web PWA stores data in browser `localStorage` and caches the app shell with a service worker. Background sync merges per-entity timestamps and deletion tombstones so offline edits combine without resurrecting deleted notes.
- **Themes:** Switch between System, Light, and Dark modes, plus optional Android Dynamic Color.

## Modules & Stack

Built with Kotlin Multiplatform, Compose Multiplatform (Material 3), SQLDelight, and `kotlinx.serialization`:

- `:composeApp`: Shared UI, editor engine, ViewModels, SQLDelight database, Markdown codec, and the `wasmJs` web PWA target.
- `:androidApp`: Android app wrapper (`minSdk 24`, `targetSdk 36`). Builds on the `beta` branch (or with `-PisBeta=true` / `ALLNOTES_IS_BETA=true`) use the `AllNotes Beta` label and `.beta` application ID so debug and release installs coexist side by side.
- `:desktopApp`: Windows desktop app and MSI/EXE packager supporting native `x64` and `arm64` targets.
- `:server`: Standalone JVM HTTP server hosting the compiled web PWA and the JSON sync API (`/api/v1/sync`, `/api/v1/health`).

## Self-Hosting with Docker

The container image bundles the pre-built `:server` distribution and Wasm web output, then serves both on port `8094`.

1. Build the server distribution and web bundle:

   ```bash
   ./gradlew :server:installDist :composeApp:wasmJsBrowserDistribution
   ```

   On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

2. Copy the environment template and set a sync token and allowed origin:

   ```bash
   cp .env.example .env
   ```

3. Make sure the external Docker network referenced in `docker-compose.yml` exists (or adjust `docker-compose.yml` to match your proxy network):

   ```bash
   docker network create web_proxy
   ```

4. Build and start the container:

   ```bash
   docker compose up -d --build
   ```

The compose file binds `127.0.0.1:8094:8094` for putting behind a reverse proxy or tunnel and persists synced workspace snapshots to `./data/workspace_server.json`. Verify the server is up at `http://localhost:8094/api/v1/health`.

To connect Android or Windows clients, open **Settings**, set **Sync Endpoint URL** to `https://notes.example.com/api/v1/sync` (or `http://localhost:8094/api/v1/sync` locally), and enter your `ALLNOTES_AUTH_TOKEN` if configured.

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8094` | HTTP listen port for the web app and sync API. |
| `DATA_DIR` | `./data` (`/data` in Docker) | Directory where `workspace_server.json` is written atomically. |
| `WEB_DIR` | `./web` (`/app/web` in Docker) | Directory containing the built Wasm web client (`WEB_DIST_DIR` is accepted as a fallback). |
| `ALLNOTES_AUTH_TOKEN` | _(empty)_ | Bearer token required on `/api/v1/sync` requests (`Authorization: Bearer <token>`). |
| `TRUST_AUTHENTIK_HEADERS` | `false` | When `true`, accepts requests authenticated by an Authentik forward-auth proxy via `X-authentik-username`. |
| `ALLNOTES_ALLOWED_ORIGINS` | _(set in `.env`)_ | Comma-separated CORS origins allowed to call the API. `localhost` and `127.0.0.1` origins are always allowed. |

If both `ALLNOTES_AUTH_TOKEN` is empty and `TRUST_AUTHENTIK_HEADERS` is `false`, `/api/v1/sync` accepts unauthenticated requests. Set a token or put the server behind forward auth before exposing it outside a private network.

## Building from Source

**Prerequisites:** JDK 17 or newer (the Docker image runs on Temurin JRE 21), plus the Android SDK (`compileSdk 37`) for Android builds.

```bash
# Android debug APK (copies output to AllNotes-debug.apk in the repo root)
./gradlew :androidApp:assembleDebug

# Windows desktop app (defaults to host CPU architecture)
./gradlew :desktopApp:run

# Windows installers (override target arch with -PtargetArch=x64 or -PtargetArch=arm64)
./gradlew :desktopApp:packageMsi -PtargetArch=x64
./gradlew :desktopApp:packageExe -PtargetArch=arm64

# Web Wasm production bundle (output in composeApp/build/dist/wasmJs/productionExecutable)
./gradlew :composeApp:wasmJsBrowserDistribution

# Run sync server locally without Docker
./gradlew :server:run

# Run unit tests
./gradlew :composeApp:testDebugUnitTest :server:test
```

On Windows, Desktop SQLite data is stored at `%APPDATA%\AllNotes\allnotes.db` (or `~/AllNotes/allnotes.db` on POSIX). For signed Android release builds, supply `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` as environment variables (or `release.keystore.path`, `release.keystore.password`, `release.key.alias`, and `release.key.password` in `local.properties`).

## Keyboard Shortcuts

On desktop and web (`Cmd` also works in place of `Ctrl`):

| Shortcut | Action |
| --- | --- |
| `Ctrl+B` / `Ctrl+I` / `Ctrl+U` | Bold, italic, or underline selection |
| `Ctrl+Shift+S` | Strikethrough selection |
| `Ctrl+K` | Insert link |
| `Ctrl+Z` / `Ctrl+Y` (`Ctrl+Shift+Z`) | Undo / Redo |
| `Ctrl+N` / `Ctrl+Shift+N` | New page / New subpage |
| `Ctrl+Shift+L` | Insert checklist |
| `Ctrl+Shift+M` | Export current page to Markdown |
| `Ctrl+F` | Focus search |
| `Ctrl+P` | Open Quick Jump |
| `Ctrl+S` | Sync now |
| `Esc` | Close active dialog, sheet, preview, or clear search |
