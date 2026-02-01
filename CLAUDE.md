# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Build and install on connected device
./gradlew assembleRelease      # Build release APK
./gradlew clean                # Clean build artifacts
```

No automated tests exist yet. The instrumentation runner is configured (`androidx.test.runner.AndroidJUnitRunner`) but no test dependencies or test files are present.

## Architecture

Android app (Kotlin, min SDK 26, target SDK 34) using Jetpack Compose, Hilt DI, and a single-module Gradle build.

### Layer Structure

**UI Layer** (`app/src/main/java/com/voice2/app/ui/`): Compose screens + Hilt-injected ViewModels using `MutableStateFlow` with a sealed `UiState` pattern (Loading/Success/Error).

**Data Layer** (`app/src/main/java/com/voice2/app/data/`):
- `api/Voice2ApiService.kt` - Retrofit interface for all backend endpoints
- `api/Models.kt` - Data classes: `Transcription`, `Tag`, `TodoItem`
- `repository/Voice2Repository.kt` - Wraps API calls, returns `Result<T>` for error handling
- `audio/AudioRecorder.kt` - MediaRecorder wrapper for M4A/AAC recording
- `preferences/SettingsPreferences.kt` - DataStore-backed settings with reactive Flows

**DI** (`app/src/main/java/com/voice2/app/di/`):
- `NetworkModule.kt` - Provides Moshi (with UUID adapter), OkHttp (with SSL trust-all for dev), Retrofit, ApiService
- `RepositoryModule.kt` - Singleton Voice2Repository binding

### Navigation

`Screen` sealed class defines routes: `ChatList`, `ChatDetail(id)`, `Todos`, `Settings`. Bottom nav bar with three tabs (Chat, Todos, Settings). Wired in `Voice2NavHost.kt`.

### Dual-Mode Transcription

- **High-Quality**: Records audio via `AudioRecorder` -> uploads M4A to `POST /audio/transcribe/` -> backend transcribes (OpenAI)
- **Fast**: Uses Android `SpeechRecognizer` on-device -> sends recognized text to `POST /chats/from_text/`

Mode is stored in DataStore preferences (`TranscriptionMode.FAST` / `HIGH_QUALITY`).

### Photo + Dictate Workflow

Camera capture occurs before recording. Photo URI is stored as a `LOCAL_PHOTO:{uri}` tag on the resulting transcription.

### Backend

Default base URL: `https://192.168.2.244:4712` (set in `app/build.gradle.kts` as `BASE_URL` BuildConfig field). Network security config (`res/xml/network_security_config.xml`) allows cleartext for local dev IPs. SSL trust-all is enabled in `NetworkModule` for self-signed dev certs.

## Coding Standards

### IDs & Types

- **UUID-first**: All entity IDs are native Postgres UUIDs (`UUID(as_uuid=True)` in SQLAlchemy, `java.util.UUID` in Kotlin). Never pass IDs as strings between layers — use typed UUIDs from API models through ViewModels.

### Backend Patterns

- **Cascade deletes**: Parent-child ORM relationships must declare `cascade="all, delete-orphan"` (e.g. `Transcription.todos`, `Transcription.virtual_albums`). For critical paths, add belt-and-suspenders explicit deletes in the service layer before the parent delete.
- **Partial updates**: Use `PATCH` with a dedicated `*Update` Pydantic schema (all fields `Optional`) for partial mutations. Use `PUT` only for full-object replacement. Use `POST /resource/{id}/toggle` for boolean toggles.
- **Schema naming**: `FooCreate` for POST bodies, `FooUpdate` for PATCH bodies, `Foo` for responses.

### Android API Layer

- **Retrofit endpoints**: Match HTTP verbs to backend — `@POST` for toggles, `@PATCH` for partial updates, `@DELETE` for deletes. The `@PUT` todo endpoint sends an incomplete body and fails with 422; always prefer the toggle endpoint for completion state changes.
- **Repository**: Every API call returns `Result<T>` via `runCatching`. New operations follow existing one-liner pattern (e.g. `suspend fun toggleTodo(id: UUID): Result<TodoItem> = runCatching { apiService.toggleTodo(id) }`).

### ViewModel Patterns

- **Sealed `UiState`**: Every screen uses `Loading`/`Success`/`Error` sealed class.
- **Pull-to-refresh**: Add `_isRefreshing: MutableStateFlow<Boolean>` alongside `_uiState`. On refresh, keep the current list visible (don't reset to `Loading`), set `isRefreshing = true`, then `false` after the fetch completes.
- **Inline edit state**: Track with a nullable ID flow (e.g. `_editingTodoId: MutableStateFlow<UUID?>`). `startEditing(id)` / `cancelEditing()` / `confirmEdit(id, newValue)`.

### Compose UI Patterns

- **Prefer native Material animations** over hand-rolled ones:
  - `AnimatedVisibility` (with `expandVertically` + `fadeIn` / `shrinkVertically` + `fadeOut`) for show/hide panels
  - `AnimatedContent` (with `togetherWith` + `SizeTransform`) for swapping between view states (e.g. display ↔ edit mode)
  - `animateItemPlacement()` on LazyColumn items with default spring — don't override with `tween` unless there's a specific reason
  - `animateColorAsState` for color transitions
  - `SwipeToDismiss` (Material3) for destructive swipe gestures
  - `PullRefreshIndicator` + `pullRefresh` (Material) for pull-to-refresh
- **Interaction patterns**: Checkbox tap for toggles, long-press (`combinedClickable`) for edit mode, swipe-left for delete.
- **LazyColumn keys**: Always provide `key = { it.id }` for stable items and string keys for section headers (e.g. `key = "header_pending"`).

### Known Technical Debt

No outstanding items. All previous debt has been resolved.
