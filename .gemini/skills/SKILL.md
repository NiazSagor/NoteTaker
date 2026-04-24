# SKILL.md — Resilient Collaborative Workspace Manager (Android)

## Document Purpose
This file is the canonical reference for AI agents implementing this assignment.
Read this entire file before writing any code. Every decision here is intentional.
Do not deviate from these instructions unless explicitly told to by the user.

---

## 1. PROJECT OVERVIEW

A local-first, real-time collaborative note-taking Android app.
Multiple users share one global workspace. All changes sync in real-time across devices.
The app works fully offline and resolves conflicts when reconnecting.

### Core User Flows
1. User opens app → sees workspace grid of note cards and standalone images
2. User creates/edits notes → changes sync to all devices in real-time
3. User drags image from device → drops onto workspace → becomes a standalone grid tile
4. User drags image from device → drops into an open note → becomes embedded in that note
5. User opens a note → uses 3 fingers on an embedded image → rotates it
6. User reorders grid tiles (notes + standalone images) by drag
7. User goes offline → edits notes → reconnects → conflict UI shown if needed

---

## 2. TECH STACK (FIXED — DO NOT SUBSTITUTE)

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Local DB | Room (WAL mode) |
| Key-Value | DataStore (Preferences) |
| Image Loading | Coil |
| Auth | Firebase Auth (Anonymous + Google Sign-In) |
| Remote DB | Firestore |
| Image Storage | Cloudinary (free tier, no card required) |
| Image Cache | Coil disk cache (local URI first, remote URL fallback) |
| Background Sync | WorkManager |
| State Survival | SavedStateHandle (process death) |
| Navigation | Compose Navigation with multi-back-stack |

### Why Cloudinary Instead of Firebase Storage
Firebase Storage requires Blaze (paid) plan as of February 2026.
Cloudinary free tier gives 25GB storage + 25GB bandwidth with no credit card.
Cloudinary returns a permanent HTTPS URL. That URL is stored in Firestore.
Room caches the local URI (before upload) and remote URL (after upload).
Coil loads from local URI first, falls back to remote URL automatically.

---

## 3. ARCHITECTURE

Pattern: MVVM + Clean Architecture + Unidirectional Data Flow

```
UI Layer (Compose Screens + Composables)
        ↕ StateFlow / events
ViewModel Layer (HiltViewModel + SavedStateHandle)
        ↕ suspend functions / Flow
Domain Layer (UseCases — optional thin wrappers)
        ↕
Repository Layer (single source of truth)
       ↙              ↘
Room (local)      Firebase + Cloudinary (remote)
```

### Key Architectural Rules
- Repository ALWAYS reads from Room. Never read directly from Firestore in a ViewModel.
- Repository writes to Room first (optimistic), then attempts remote write.
- Firestore listeners write incoming changes INTO Room. UI observes Room via Flow.
- ViewModels never import Room DAOs or Firestore directly. Always go through Repository.
- All Firestore and Cloudinary calls happen on Dispatchers.IO.
- UI state is StateFlow<ScreenUiState> — a single sealed data class per screen.

---

## 4. MODULE STRUCTURE
Single module project with following directory structure:
```
app/
├── :core:data
│     ├── db/              ← Room database, DAOs, entities
│     ├── datastore/       ← Preferences DataStore
│     ├── repository/      ← Repository implementations
│     └── sync/            ← WorkManager sync workers
│
├── :core:domain
│     ├── model/           ← Domain models (not Room entities)
│     ├── usecase/         ← UseCases (keep thin — just orchestration)
│     └── conflict/        ← Conflict detection + resolution logic
│
├── :core:network
│     ├── firebase/        ← Firestore wrappers, Auth
│     └── cloudinary/      ← Cloudinary upload client
│
├── :core:ui
│     ├── theme/           ← MaterialTheme, typography, colors
│     ├── components/      ← Shared Composables (tiles, dialogs)
│     └── gesture/         ← Reusable gesture modifiers
│
├── :feature:workspace
│     ├── WorkspaceScreen  ← Main grid view
│     └── WorkspaceViewModel
│
├── :feature:editor
│     ├── NoteEditorScreen ← Note editing with embedded images
│     └── NoteEditorViewModel
│
├── :feature:conflict
│     ├── ConflictScreen   ← Side-by-side conflict resolution UI
│     └── ConflictViewModel
│
└── :feature:auth
      ├── AuthScreen
      └── AuthViewModel
```

---

## 5. DATA MODELS

### 5.1 Design Principles
- Every entity has a String UUID `id` generated client-side (never rely on Firestore auto-ID alone).
- Every entity has `createdAt` and `updatedAt` as Long (epoch millis UTC).
- Every entity has `syncStatus: SyncStatus` for offline tracking.
- Every entity has `localVersion: Int` and `remoteVersion: Int` for conflict detection.
- Every entity has `isDeleted: Boolean` for soft deletes (sync-safe tombstoning).
- Optional debug fields are marked with `// DEBUG` comment — keep them, they cost nothing.
- Firestore document structure mirrors Room entity fields 1:1 to avoid mapping bugs.

### 5.2 SyncStatus Enum

```kotlin
// core/data/db/entity/SyncStatus.kt
enum class SyncStatus {
    SYNCED,     // local matches remote
    PENDING,    // local change not yet pushed to Firestore
    CONFLICT,   // diverged — needs manual resolution
    UPLOADING,  // asset currently being uploaded to Cloudinary
    ERROR       // last sync attempt failed (will retry)
}
```

### 5.3 Room Entities

#### WorkspaceEntity
Represents the shared workspace. One per app (single global workspace).
```kotlin
@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,                // UUID, shared across all users
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,                     // Firebase UID
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,       // DEBUG: when did we last try to sync
    val lastSyncError: String? = null          // DEBUG: last error message if any
)
```

#### GridElementEntity
Represents any tile in the workspace grid — either a NOTE or a STANDALONE_IMAGE.
Both notes and standalone images share this table so they can be ordered together.
```kotlin
@Entity(tableName = "grid_elements")
data class GridElementEntity(
    @PrimaryKey val id: String,                // UUID
    val workspaceId: String,                   // FK to WorkspaceEntity
    val type: GridElementType,                 // NOTE or STANDALONE_IMAGE
    val orderIndex: Double,                    // fractional index for ordering
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,                     // Firebase UID

    // Only populated when type == NOTE
    val noteId: String? = null,

    // Only populated when type == STANDALONE_IMAGE
    val localImageUri: String? = null,         // file:// URI before upload
    val remoteImageUrl: String? = null,        // Cloudinary HTTPS URL after upload
    val uploadStatus: UploadStatus = UploadStatus.NONE,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val debugTag: String? = null               // DEBUG: human label for logging
)

enum class GridElementType { NOTE, STANDALONE_IMAGE }
enum class UploadStatus { NONE, PENDING, UPLOADING, DONE, FAILED }
```

#### NoteEntity
The actual note content. Always referenced by a GridElementEntity of type NOTE.
```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,                // UUID — same as GridElementEntity.noteId
    val workspaceId: String,
    val title: String,
    val content: String,                       // plain text for now
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,                     // Firebase UID
    val lastEditedBy: String,                  // Firebase UID of last editor

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // Conflict snapshots — stored locally when conflict detected
    // Null when no conflict
    val conflictLocalSnapshot: String? = null,   // JSON of local NoteEntity at conflict time
    val conflictRemoteSnapshot: String? = null,  // JSON of incoming remote NoteEntity
    val conflictDetectedAt: Long? = null,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val editCount: Int = 0                     // DEBUG: how many times edited locally
)
```

#### NoteImageEntity
Images that are EMBEDDED INSIDE a note (not standalone grid tiles).
These are the images that support 3-finger rotation.
```kotlin
@Entity(tableName = "note_images")
data class NoteImageEntity(
    @PrimaryKey val id: String,                // UUID
    val noteId: String,                        // FK to NoteEntity
    val workspaceId: String,
    val orderInNote: Int,                      // display order within the note
    val localImageUri: String?,                // file:// URI before upload
    val remoteImageUrl: String?,               // Cloudinary HTTPS URL after upload
    val rotationDegrees: Float = 0f,           // persisted rotation from 3-finger gesture
    val uploadStatus: UploadStatus = UploadStatus.NONE,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localVersion: Int = 0,
    val remoteVersion: Int = 0,
    val isDeleted: Boolean = false,

    // DEBUG fields
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val rotationHistory: String? = null        // DEBUG: JSON array of last 5 rotation values
)
```

#### ConflictEntity
Stored whenever a conflict is detected between local and remote versions.
Separate table keeps NoteEntity clean and makes conflict UI easier to query.
```kotlin
@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey val id: String,                // UUID
    val noteId: String,                        // which note has conflict
    val workspaceId: String,
    val localSnapshot: String,                 // JSON of local NoteEntity
    val remoteSnapshot: String,                // JSON of incoming remote NoteEntity
    val localVersion: Int,
    val remoteVersion: Int,
    val detectedAt: Long,
     // Add these two fields to ConflictEntity
     val expectedVersion: Int,        // remoteVersion captured when conflict was detected
                                 // passed to Firestore transaction as baseline check
    val conflictRoundCount: Int = 0  // how many times this conflict has re-triggered
                                 // show warning banner in UI when >= 3
    val resolvedAt: Long? = null,
    val isResolved: Boolean = false,
    val resolutionStrategy: ResolutionStrategy? = null,
    val resolvedBy: String? = null,            // Firebase UID of who resolved

    // DEBUG fields
    val conflictDiffSummary: String? = null    // DEBUG: short human-readable diff
)

enum class ResolutionStrategy { KEEP_LOCAL, KEEP_REMOTE, MANUAL_MERGE }
```

### 5.4 Firestore Document Structure

Mirrors Room entities exactly. Each Firestore document = one Room entity.

```
/workspaces/{workspaceId}
    → WorkspaceEntity fields

/workspaces/{workspaceId}/gridElements/{elementId}
    → GridElementEntity fields

/workspaces/{workspaceId}/notes/{noteId}
    → NoteEntity fields (WITHOUT conflictLocalSnapshot/Remote — those are local only)

/workspaces/{workspaceId}/noteImages/{imageId}
    → NoteImageEntity fields (WITHOUT rotationHistory — local only)

/workspaces/{workspaceId}/presence/{userId}
    → { userId, displayName, lastSeen: Long, currentNoteId: String? }
    → Used for "who is online" awareness. Write on every screen change.
```

### 5.5 Firestore Field Exclusions
These fields exist in Room but are NEVER written to Firestore:
- `conflictLocalSnapshot` — local only
- `conflictRemoteSnapshot` — local only
- `conflictDetectedAt` — local only
- `rotationHistory` — local debug only
- `debugTag` — local debug only
- `lastSyncError` — local only
- `syncStatus` — derived locally
- `localVersion` — local only (remoteVersion IS synced)

---

## 6. CONFLICT DETECTION & RESOLUTION

### 6.1 The Version Model
Each synced entity tracks two version counters:
- `localVersion`: increments on every local edit (starts at 0)
- `remoteVersion`: the last remote version successfully applied locally

On every Firestore write, the document's `remoteVersion` is incremented by 1.

ADDITIONAL RULE:
The expectedVersion is captured at the moment conflict detection fires
(when the remote snapshot arrives and Case 4 is identified).
This value is held in the ConflictEntity and passed to the push
transaction as the baseline to check against.
It is NOT re-read from Room just before pushing — it must be the
version that was current when the conflict was first detected,
otherwise the check loses its meaning.

### 6.2 Conflict Detection Logic

Run this check every time a Firestore snapshot arrives for a note:

```
INCOMING: remote document with remoteVersion = R
LOCAL:    Room row with localVersion = L, remoteVersion = LR

CASE 1 — No local changes, remote advanced:
    L == 0 AND R > LR
    → Safe. Apply remote to Room. Update remoteVersion = R.
    → No user action needed.

CASE 2 — Local change, remote unchanged:
    L > 0 AND R == LR
    → Safe. Our local change is ahead. Let WorkManager push it.
    → No user action needed.

CASE 3 — Clean fast-forward (remote advanced by exactly 1):
    L > 0 AND R == LR + 1
    → Likely safe. Apply remote delta, keep local draft separately.
    → Auto-merge: append remote change, keep unsaved local draft.
    → Notify user softly (snackbar, not blocking dialog).

CASE 4 — TRUE CONFLICT (both sides diverged):
    L > 0 AND R > LR + 1
    → Both local and remote changed from same base version.
    → Create ConflictEntity in Room.
    → Set NoteEntity.syncStatus = CONFLICT.
    → Show ConflictResolutionScreen (blocking for that note).
```

### 6.3 When to Prompt User
ONLY prompt the user (show ConflictResolutionScreen) in CASE 4.
Cases 1, 2, 3 are handled silently or with a non-blocking snackbar.
Never block the user on a conflict for a note they are not currently viewing.
Show a badge/indicator on conflicted note tiles in the workspace grid.

RE-PROMPT RULE:
If a ConflictOnPushException is caught during resolution push
AND re-evaluation shows a large gap (Case 4 again):
    Dismiss current resolution UI
    Create a new ConflictEntity with fresh snapshots
    Re-show conflict UI with updated versions
    Increment a conflictRoundCount field on ConflictEntity

If conflictRoundCount >= 3:
    Show warning banner above conflict UI:
    "This note is being actively edited by others"
    This does not block resolution — just informs the user

### 6.4 Conflict Resolution UI

```
┌────────────────────────────────────────────────┐
│  ⚠️  Conflict on "Note Title"                  │
│  Someone edited this while you were offline.   │
├─────────────────────┬──────────────────────────┤
│   YOUR VERSION      │   INCOMING VERSION        │
│   (local)           │   (from server)           │
│                     │                           │
│   [scrollable text] │   [scrollable text]       │
│                     │                           │
├─────────────────────┴──────────────────────────┤
│  [Keep Mine]   [Keep Theirs]   [Merge Both]    │
└────────────────────────────────────────────────┘
```

- Keep Mine: write local snapshot to Firestore, increment remoteVersion
- Keep Theirs: apply remote snapshot to Room, clear local draft
- Merge Both: show editable text field pre-filled with both versions concatenated,
  user manually edits, then confirm saves merged result

After resolution:
- Delete ConflictEntity (or mark isResolved = true)
- Set NoteEntity.syncStatus = PENDING (to push resolved version)
- Notify other participants via Firestore presence update

### 6.5 Conflict for Non-Text Fields
For `orderIndex`, `rotationDegrees`, `uploadStatus` — use LAST WRITE WINS.
These are numeric/positional values where conflict is low-stakes.
Do not show a conflict dialog for these fields.

---

## 7. SYNC ARCHITECTURE

### 7.1 Write Path (Local → Remote)

```
User makes any change
    ↓
1. Write to Room immediately (optimistic write)
   Set syncStatus = PENDING
   Increment localVersion
    ↓
2. Emit to UI via Flow (instant, no waiting for network)
    ↓
3. Debounce 300ms (text fields) or 0ms (structural changes like reorder)
    ↓
4. Attempt Firestore write on Dispatchers.IO
    ↓
   Success → set syncStatus = SYNCED, set remoteVersion = incoming version
   Failure → stays PENDING, WorkManager will retry

PUSH RULE (CONFLICT RESOLUTIONS ONLY):
Never use .set() or .update() directly when pushing a conflict resolution.
Always use a Firestore Transaction that:
  1. Reads current remoteVersion inside the transaction
  2. Compares against expectedVersion (the version we resolved against)
  3. Writes only if they match → increment remoteVersion by 1
  4. Throws ConflictOnPushException if they do not match
  5. Caller catches exception → re-runs conflict detection
     against the newly fetched version

Normal edits (non-resolution) do NOT need this transaction check.
The Firestore snapshot listener continuously guards normal edits.
The transaction check is specifically for the window where the user
is inside the conflict resolution UI and the listener is not acting.
```

### 7.2 Read Path (Remote → Local)

```
Firestore .addSnapshotListener() for workspace collection
    ↓
Incoming snapshot on Dispatchers.IO
    ↓
Run conflict detection logic (Section 6.2)
    ↓
CASE 1/2/3 → write to Room → Flow emits → UI updates
CASE 4      → write ConflictEntity → set syncStatus = CONFLICT
             → UI observes conflict count → shows badge/dialog
```

### 7.3 Debounce Strategy

| Action | Debounce | Reason |
|---|---|---|
| Note text typing | 300ms | Avoid write-per-keystroke |
| Note title typing | 300ms | Same |
| Image rotation (3-finger) | 0ms on gesture END | Write only on finger lift |
| Grid reorder (drag) | 0ms on drag END | Write only on drop |
| Image position | 0ms on drag END | Write only on drop |
| Note creation/deletion | 0ms | Structural, immediate |
| Presence update | 5000ms | Low priority |

NEVER write to Firestore on every frame during a gesture.
ALWAYS write to Room optimistically first, then sync.

### 7.4 WorkManager Offline Queue

On app start, WorkManager enqueues a `SyncPendingWorker`:
```
Constraints: NetworkType.CONNECTED
InputData: workspaceId
Backoff: EXPONENTIAL, initial 10s
```

Worker queries Room for all entities with syncStatus = PENDING or ERROR,
processes them in createdAt ASC order (FIFO), retries on failure.

### 7.5 Conflict Push — Transaction With Retry

When user confirms a conflict resolution, use this flow:

var retryCount = 0
val maxRetries = 3

while retryCount < maxRetries:
    try:
        START Firestore transaction
            READ current remoteVersion from Firestore
            IF currentRemoteVersion != expectedVersion:
                THROW ConflictOnPushException(
                    expected = expectedVersion,
                    actual = currentRemoteVersion
                )
            WRITE resolved content
            SET remoteVersion = currentRemoteVersion + 1
        END transaction
        BREAK  ← success, exit loop

    catch ConflictOnPushException:
        retryCount++

        IF retryCount >= maxRetries:
            Save resolved draft locally in Room
            Show message: "This note is being heavily edited.
                           Your version is saved. Try again later."
            BREAK

        Re-run conflict detection against e.actualVersion
            IF gap is small → silent merge → retry push
            IF gap is large → show conflict UI again with new snapshots


ConflictOnPushException is a local exception class, not a Firestore error.
It is thrown manually inside the transaction when versions do not match.

---

## 8. WORKSPACE GRID — TILE VIEW & REORDERING

### 8.1 Grid Layout
Use `LazyVerticalStaggeredGrid` or `LazyVerticalGrid` (2 columns default).
Each cell renders either a NoteCardTile or a StandaloneImageTile.
Both tile types share the same `orderIndex: Double` space.

### 8.2 Fractional Indexing for Order
Never use integer positions (requires rewriting all indices on every move).
Use Double fractional indexing:

```
Initial creation: orderIndex = createdAt.toDouble() (timestamp as index)

Inserting between A (index=1.0) and B (index=2.0):
    newIndex = (1.0 + 2.0) / 2.0 = 1.5

Inserting between A (index=1.0) and new (index=1.5):
    newIndex = (1.0 + 1.5) / 2.0 = 1.25
```

Gap exhaustion prevention: if gap < 0.001, run a re-normalization pass
that reassigns all indices as 1.0, 2.0, 3.0... and syncs all to Firestore.
This is rare. Trigger it in a background coroutine, not on the UI thread.

### 8.3 Drag to Reorder Implementation

```kotlin
// Use detectDragGesturesAfterLongPress
// During drag: show drag shadow, highlight target slot
// On drop: calculate new orderIndex, write to Room, debounce 0ms → Firestore

// Key states to track in ViewModel:
data class DragState(
    val draggingElementId: String?,
    val dragOffsetY: Float,
    val targetSlotIndex: Int?
)
```

Only write the new orderIndex on drop. Do not write during drag frames.

---

## 9. ASSET DRAG & DROP INTO WORKSPACE

### 9.1 Dropping Image onto Workspace Grid
Creates a new `GridElementEntity` of type `STANDALONE_IMAGE`.

```
User drags image from gallery or file manager
    ↓
DragAndDropTarget on workspace grid receives onDrop()
    ↓
Extract URI from ClipData
    ↓
Copy file to app-internal cache (so URI remains valid after source app closes)
    ↓
Create GridElementEntity(type=STANDALONE_IMAGE, localImageUri=cachedUri,
    uploadStatus=PENDING, syncStatus=PENDING)
    ↓
Write to Room immediately → UI shows tile with local image instantly
    ↓
Enqueue CloudinaryUploadWorker(entityId, localUri)
    ↓
Worker uploads to Cloudinary → gets back HTTPS URL
    ↓
Update GridElementEntity(remoteImageUrl=url, uploadStatus=DONE)
    ↓
Write remoteImageUrl to Firestore → other devices load image via Coil
```

### 9.2 Dropping Image into an Open Note
Creates a new `NoteImageEntity` linked to the current note.

```
User drags image while NoteEditorScreen is open
    ↓
DragAndDropTarget on NoteEditorScreen receives onDrop()
    ↓
Extract URI, copy to app-internal cache
    ↓
Create NoteImageEntity(noteId=currentNoteId, localImageUri=cachedUri,
    orderInNote=currentImageCount, uploadStatus=PENDING)
    ↓
Write to Room → UI shows image in note immediately (from local URI via Coil)
    ↓
Enqueue CloudinaryUploadWorker(noteImageId, localUri)
    ↓
Worker uploads → gets remoteImageUrl
    ↓
Update NoteImageEntity(remoteImageUrl=url, uploadStatus=DONE)
    ↓
Write to Firestore → other devices see embedded image
```

### 9.3 Image Loading Strategy (Coil)
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(noteImage.remoteImageUrl ?: noteImage.localImageUri)
        .diskCacheKey(noteImage.id)
        .memoryCacheKey(noteImage.id)
        .crossfade(true)
        .build(),
    contentDescription = null
)
```
Always prefer remoteImageUrl if available.
Fall back to localImageUri if upload not done yet.
This means the uploader never sees a blank image — local shows immediately.

### 9.4 Cloudinary Upload Implementation

```kotlin
// core/network/cloudinary/CloudinaryUploadClient.kt
// Use Cloudinary Android SDK or plain multipart HTTP via OkHttp
// Upload endpoint: https://api.cloudinary.com/v1_1/{cloudName}/image/upload
// Required params: file (multipart), upload_preset (unsigned preset)
// Returns: { secure_url: "https://res.cloudinary.com/..." }

// Store cloudName and upload_preset in BuildConfig (not hardcoded)
// Never store Cloudinary API secret in the app
// Use unsigned upload preset for mobile uploads
```

---

## 10. THREE-FINGER ROTATION (EMBEDDED NOTE IMAGES)

### 10.1 Applies Only To
`NoteImageEntity` instances displayed inside `NoteEditorScreen`.
Does NOT apply to standalone grid images.

### 10.2 Finger Protocol (Exact Sequence)
```
Pointer 0 lands  → SELECT image (show selection border/ring)
Pointer 1 lands  → ENABLE rotation mode (enter ROTATING state)
Pointer 2 lands  → SHOW HUD (rotation arc overlay)

While all 3 fingers active:
    Use pointer 1 and pointer 2 positions to compute rotation angle
    pivot = center of image bounding box
    angle = atan2(pointer2.y - pivot.y, pointer2.x - pivot.x)
           - atan2(pointer1.y - pivot.y, pointer1.x - pivot.x)
    Apply angle delta to current rotationDegrees
    HUD redraws with current degree value

Any finger lifts:
    EXIT ROTATING state → back to SELECTED
    HIDE HUD
    Write final rotationDegrees to Room (single atomic write)
    Debounce 0ms → push to Firestore
```

### 10.3 State Machine
```kotlin
sealed class ImageInteractionState {
    object Idle : ImageInteractionState()
    data class Selected(val imageId: String) : ImageInteractionState()
    data class Rotating(
        val imageId: String,
        val currentDegrees: Float,
        val showHud: Boolean
    ) : ImageInteractionState()
}
```

Track this state in NoteEditorViewModel using StateFlow.
Only one image can be in Selected or Rotating state at a time.
Tapping another image while one is selected → deselect first, select new.

### 10.4 HUD Implementation
Draw with Canvas composable as an overlay on the image:
```kotlin
// Draw arc from 0° to currentRotationDegrees
// Draw degree text in center: "47°"
// Color: white with 0.85 alpha, 3dp stroke
// Only visible when state is Rotating
// Animate in/out with animateFloatAsState
```

### 10.5 Persist Only on Gesture End
NEVER write rotation to Room during active gesture frames.
ONLY write when all fingers have lifted (onDragEnd / last pointer up).
This prevents ~60 Room writes per second during rotation.

### 10.6 System Gesture Conflicts
Add `systemGestureExclusionRects` around the image area to prevent
Android's system back gesture from interfering with 3-finger detection.

---

## 11. CONCURRENT NOTE SESSIONS

### 11.1 What It Means
One local user can have multiple notes open simultaneously (like browser tabs).
Switching between open notes must NOT lose unsaved draft text.

### 11.2 Implementation

Each open note session = one entry in Compose Navigation back stack.
Each entry has its own scoped ViewModel instance.
ViewModel holds draft state in SavedStateHandle.

```kotlin
// In NoteEditorViewModel
private val noteId: String = checkNotNull(savedStateHandle["noteId"])

// Draft buffer — survives process death
var draftContent: String
    get() = savedStateHandle["draft"] ?: ""
    set(value) { savedStateHandle["draft"] = value }
```

WorkspaceViewModel tracks open session IDs:
```kotlin
// Persisted across process death
var openSessionIds: List<String>
    get() = savedStateHandle["openSessions"] ?: emptyList()
    set(value) { savedStateHandle["openSessions"] = value }
```

### 11.3 Auto-Save
Separate from draft buffer. Auto-save writes to Room on 500ms inactivity:
```kotlin
snapshotFlow { draftContent }
    .debounce(500)
    .distinctUntilChanged()
    .onEach { content ->
        noteRepository.saveNote(noteId, content)  // writes to Room → syncs
    }
    .launchIn(viewModelScope)
```

Draft buffer = what user sees (instant).
Auto-save = what gets persisted (debounced).
Both use SavedStateHandle so neither is lost on process death.

---

## 12. FAULT TOLERANCE & PROCESS DEATH

### 12.1 SavedStateHandle Usage
Every ViewModel that holds UI state MUST use SavedStateHandle for:
- Open note session IDs
- Current draft text per note
- Active conflict IDs
- Scroll positions (if not handled by rememberSaveable)
- Any in-progress gesture state (rotation degrees mid-gesture)

### 12.2 Lifecycle Hooks
```
ON_PAUSE  → flush any in-memory gesture state to Room atomically
ON_STOP   → cancel in-flight Cloudinary uploads gracefully
           → mark affected assets as uploadStatus = PENDING
           → WorkManager re-picks uploads on resume
ON_DESTROY → nothing extra needed (Room + SavedStateHandle already safe)
```

### 12.3 Interruptions (Phone Calls, Alerts)
When a phone call arrives mid-gesture:
- Activity receives onPause()
- ViewModel's ON_PAUSE observer writes current rotation to Room
- Gesture pointer tracking is reset
- On resume: image shows last-saved rotation, no corrupt state

### 12.4 Room WAL Mode
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "workspace.db")
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .build()
```
WAL mode ensures reads never block writes and writes are crash-safe.
All multi-entity operations MUST use @Transaction.

---

## 13. AUTHENTICATION

### 13.1 Flow
1. App opens → check if Firebase user exists
2. If no user → sign in anonymously (silent, no UI)
3. Show optional "Link Account" prompt to upgrade to Google Sign-In
4. All Firestore reads/writes are authenticated (Firestore rules require auth)

### 13.2 User Identity
Use Firebase UID as the user identifier everywhere.
Store UID in `createdBy` and `lastEditedBy` fields.
Presence document keyed by UID.

---

## 14. SECURITY

### 14.1 Firestore Rules (Required)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /workspaces/{workspaceId}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```
This is sufficient for an assignment. Production would scope by membership.

### 14.2 Cloudinary
Use unsigned upload preset. Never embed Cloudinary API secret in APK.
Store cloudName and uploadPreset in BuildConfig fields from local.properties.

### 14.3 Local Storage
Room database does not need encryption for this assignment.
If encrypting: use SQLCipher. Key stored in Android Keystore.
Auth tokens are managed by Firebase SDK (stored in EncryptedSharedPreferences automatically).

---

## 15. WHAT NOT TO DO

### Architecture
- DO NOT read from Firestore directly in a ViewModel or Composable
- DO NOT write to Firestore on every keystroke or gesture frame
- DO NOT use GlobalScope for coroutines — use viewModelScope or specific CoroutineScope
- DO NOT skip the Room write — always write local first, then remote
- DO NOT share a single ViewModel between multiple open note sessions

### Data Model
- DO NOT use Firestore auto-generated IDs as primary keys — use client-side UUIDs
- DO NOT store Base64 images in Firestore — always use Cloudinary URL
- DO NOT add nullable fields for non-optional data — use sensible defaults instead
- DO NOT remove the DEBUG fields — they cost nothing and save hours of debugging

### Gestures
- DO NOT write rotation to Room on every gesture frame (60 writes/sec = data corruption risk)
- DO NOT use GestureDetector (View system) — use Compose pointerInput exclusively
- DO NOT allow more than one image to be in Rotating state simultaneously

### Conflict Resolution
- DO NOT show conflict dialog for every remote update — only CASE 4 (Section 6.2)
- DO NOT block the entire app for a conflict — only the affected note
- DO NOT auto-resolve text conflicts silently — always show UI for CASE 4
- DO NOT use CRDT — the spec explicitly requires a manual merge UI

### Sync
- DO NOT poll Firestore — use snapshot listeners exclusively
- DO NOT cancel WorkManager sync jobs on app close — let them complete
- DO NOT write orderIndex changes to Firestore during drag — only on drop
- DO NOT use .set() or .update() directly for conflict resolution pushes
  Always use a Firestore Transaction with version check

- DO NOT re-read expectedVersion from Room just before pushing
  Capture it at conflict detection time and hold it in ConflictEntity

- DO NOT retry conflict push infinitely
  Cap at 3 retries then save draft locally and surface a message

### Scope
- DO NOT build a Notion-style block editor — it is not required by the spec
- DO NOT add real-time cursor presence (showing other users' cursors) — not required
- DO NOT implement rich text formatting (bold, italic) — plain text is sufficient
- DO NOT implement workspace membership / invite flow — single global workspace is fine
- DO NOT implement image resizing — only rotation is required by spec
- DO NOT implement undo/redo — not required by spec

---

## 16. FLOW & COROUTINE PATTERNS

### 16.1 Repository Pattern
```kotlin
// Always return Flow<Result<T>> for async data
fun observeNotes(workspaceId: String): Flow<List<NoteEntity>> =
    noteDao.observeByWorkspace(workspaceId)  // Room returns Flow automatically

// Suspend functions for one-shot operations
suspend fun saveNote(entity: NoteEntity): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        noteDao.upsert(entity)
        firestoreSource.upsertNote(entity)
    }
}
```

### 16.2 ViewModel Collection
```kotlin
// Collect in ViewModel, not in Composable
noteRepository.observeNotes(workspaceId)
    .onEach { notes -> _uiState.update { it.copy(notes = notes) } }
    .catch { e -> _uiState.update { it.copy(error = e.message) } }
    .launchIn(viewModelScope)
```

### 16.3 Firestore Listener as Flow
```kotlin
fun observeRemoteNotes(workspaceId: String): Flow<List<NoteDto>> = callbackFlow {
    val listener = firestore
        .collection("workspaces/$workspaceId/notes")
        .addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            snapshot?.let { trySend(it.toNoteDtoList()) }
        }
    awaitClose { listener.remove() }
}
```

---

## 17. TESTING NOTES (FOR AGENT REFERENCE)

- Unit test: ConflictDetector — cover all 4 cases in Section 6.2
- Unit test: FractionalIndexCalculator — cover gap exhaustion edge case
- Unit test: RotationGestureHandler — verify write only on gesture end
- Integration test: Repository sync round-trip (Room → Firestore → Room)
- UI test: Conflict resolution screen — all 3 resolution paths
- Manual test: Force process death (adb shell am kill) → reopen → verify state restored

---

## 18. QUICK REFERENCE — ENTITY TO SCREEN MAPPING

| Screen | Primary Entity | Secondary Entities |
|---|---|---|
| WorkspaceScreen | GridElementEntity | NoteEntity (title only) |
| NoteEditorScreen | NoteEntity | NoteImageEntity |
| ConflictScreen | ConflictEntity | NoteEntity (both snapshots) |
| AuthScreen | Firebase User | — |

---

## 19. OPTIONAL ENHANCEMENTS (ONLY IF TIME PERMITS)

These are NOT required by spec. Implement only after all core features work:
- Pinch-to-zoom on embedded images
- Note card color theming
- Search/filter notes in workspace
- Export note as text file
- Presence indicators (green dot on tiles being edited by others)

---

*End of SKILL.md — Version 1.0*
*This document is the single source of truth for all implementation decisions.*
*Update this file if any architectural decision changes during implementation.*
