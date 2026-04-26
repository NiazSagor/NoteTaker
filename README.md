# NoteTaker
**A Local-First, Real-Time Collaborative Note-Taking Android App**

---

## 🎯 Overview

NoteTaker is a multi-user workspace where notes and images sync in real-time across devices. It works fully offline and intelligently resolves conflicts when reconnecting.

### Core Features
- ✅ Real-time synchronization across all devices
- ✅ Offline-first with automatic conflict resolution
- ✅ Drag-to-reorder grid tiles (fractional indexing)
- ✅ Embed images in notes with 3-finger rotation
- ✅ Drag-and-drop assets into workspace
- ✅ Concurrent note editing sessions

[Watch demo](video/1.mp4)

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose |
| **DI** | Hilt |
| **Async** | Kotlin Coroutines + Flow |
| **Local DB** | Room (WAL mode) |
| **Remote DB** | Firestore |
| **Image Storage** | Cloudinary (free tier) |
| **Image Loading** | Coil |
| **Auth** | Firebase Auth (Anonymous + Google) |
| **Background Sync** | WorkManager |

---

## ⚡ Real-Time Sync Architecture

### The Sync Loop

```
USER EDIT
   ↓
WRITE TO ROOM (optimistic)
   ↓
EMIT TO UI (instant)
   ↓
DEBOUNCE (300ms for text, 0ms for gestures)
   ↓
ATTEMPT FIRESTORE WRITE
   ↓
SUCCESS: Mark SYNCED
FAILURE: Mark PENDING (retry via WorkManager)
```

### Data Flow

**Write Path (Local → Remote):**
- User makes change → write to Room immediately
- UI updates instantly from Flow
- Debounced push to Firestore on IO thread
- If offline, WorkManager retries on reconnect

**Read Path (Remote → Local):**
- Firestore snapshot listener watches for changes
- Runs conflict detection on every incoming update
- Writes to Room based on conflict case
- UI observes Room via Flow (single source of truth)

### Debounce Strategy

| Action | Debounce | Reason |
|--------|----------|--------|
| Text typing | 300ms | Avoid keystroke-per-write |
| Grid reorder (drag) | 0ms on drop | Only write on release |
| 3-finger rotation | 0ms on gesture end | Only write on finger lift |
| Note creation | 0ms | Structural, immediate |
| Presence update | 5000ms | Low priority |

**Golden Rule:** Never write to Firestore during gesture frames. Always debounce to write only when gesture ends.

---

## 🔴 Conflict Detection & Resolution

### The Version Model

Every synced entity tracks two version counters:
- **`localVersion`** (L): Increments on every local edit
- **`remoteVersion`** (LR): Last remote version we applied locally

When Firestore receives a write, it increments the document's `remoteVersion` by 1.

### 4-Case Conflict Logic

When a Firestore snapshot arrives with remote version **R**, compare against local state (L, LR):

```
┌─────────────────────────────────────────────────────┐
│  CASE 1: L=0, R > LR                                │
│  No local changes, remote advanced                  │
│  Action: Apply remote safely, no user action needed │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  CASE 2: L > 0, R = LR                              │
│  Local ahead, remote unchanged                      │
│  Action: Let WorkManager push local change         │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  CASE 3: L > 0, R = LR + 1                          │
│  Clean fast-forward (remote advanced by 1)          │
│  Action: Apply remote, keep unsaved draft separate │
│  → Soft notification (snackbar, not blocking)      │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  CASE 4: L > 0, R > LR + 1                          │
│  TRUE CONFLICT (both sides diverged)                │
│  Action: Create ConflictEntity, show resolution UI │
│  → User must choose: Keep Mine / Keep Theirs /     │
│     Merge Both                                      │
└─────────────────────────────────────────────────────┘
```

### Conflict Resolution UI

```
┌────────────────────────────────────────────┐
│  ⚠️  Conflict on "Note Title"              │
│  Someone edited this while you were away   │
├─────────────────┬──────────────────────────┤
│   YOUR VERSION  │   THEIR VERSION          │
│   (local)       │   (from server)          │
│                 │                          │
│   [scrollable]  │   [scrollable]           │
│                 │                          │
├─────────────────┴──────────────────────────┤
│  [Keep Mine] [Keep Theirs] [Merge Both]    │
└────────────────────────────────────────────┘
```

**Resolution options:**
- **Keep Mine:** Push local version, increment remoteVersion
- **Keep Theirs:** Apply remote to local, discard draft
- **Merge Both:** User edits concatenated text, then save

### Transaction Conflict Push

When pushing a conflict resolution, use a Firestore transaction that:

```
START TRANSACTION
  ├─ READ current remoteVersion from Firestore
  ├─ COMPARE against expectedVersion (version we resolved against)
  ├─ IF MATCH: Write resolved content, increment remoteVersion
  └─ IF NO MATCH: Throw ConflictOnPushException
END TRANSACTION
```

If `ConflictOnPushException` occurs:
- Re-run conflict detection against the new remote version
- If gap is small → auto-merge and retry
- If gap is large → show conflict UI again with fresh snapshots
- Cap retries at 3, then save locally and show warning

**Why the transaction?** Prevents the window between reading the version and writing the resolution from allowing another conflict to sneak in.

---

## 🖐️ Three-Finger Rotation (Embedded Images)

### Finger Detection Sequence

The rotation gesture only works on images **embedded inside notes** (not standalone grid images).

```
POINTER 0 LANDS
  ↓
SHOWS selection ring around image

POINTER 1 LANDS
  ↓
ENABLES rotation mode

POINTER 2 LANDS
  ↓
SHOWS HUD overlay with rotation arc and degree value

WHILE ALL 3 FINGERS ACTIVE
  ├─ Compute angle between pointer 1 and pointer 2
  ├─ Calculate delta from previous angle
  ├─ Apply delta to rotationDegrees
  └─ HUD updates with current degree value

ANY FINGER LIFTS
  ↓
WRITE final rotationDegrees to Room (single atomic write)
  ↓
PUSH to Firestore (0ms debounce)
```

### State Machine

```
IDLE
  ↓ (tap image)
SELECTED (show ring)
  ↓ (2nd finger lands)
ROTATING (show HUD)
  ↓ (any finger lifts)
SELECTED (hide HUD)
  ↓ (tap elsewhere)
IDLE
```

### Critical Constraints

- ✋ **Only write on gesture END.** Never write during active frames (~60 fps = 60 writes/sec = corruption)
- 🔒 **Only one image rotating at a time.** Tapping another image while rotating → deselect first
- 🛡️ **System gesture exclusion.** Add `systemGestureExclusionRects` to prevent Android back gesture interference
- 📱 **Process death safe.** SavedStateHandle persists in-progress rotation on pause

---

## 📊 Fractional Indexing (Smart Reordering)

### The Problem with Integer Indices

If tiles are at positions 1, 2, 3, inserting between 1 and 2 requires shifting all indices:
- Tile 1 → stays 1
- Tile 2 → becomes 3
- Tile 3 → becomes 4
- New tile → 2

**Result:** Every insert cascades updates to ALL tiles after it. Doesn't scale with concurrent edits.

### The Solution: Fractional Indices

Use **Double** values as indices, allowing infinite subdivisions without moving existing tiles.

```
Initial order:
  Tile A: orderIndex = 1000.0 (created at timestamp 1000)
  Tile B: orderIndex = 2000.0 (created at timestamp 2000)

Insert between A and B:
  New Tile: orderIndex = (1000.0 + 2000.0) / 2 = 1500.0
  ✅ A and B unchanged!

Insert between A and New:
  Another: orderIndex = (1000.0 + 1500.0) / 2 = 1250.0
  ✅ A, B, and New unchanged!

Insert between Another and A:
  Yet Another: orderIndex = (1000.0 + 1250.0) / 2 = 1125.0
  ✅ All previous unchanged!
```

### Gap Exhaustion Prevention

After many insertions, the gap between tiles shrinks (floating-point precision limit around gap < 0.001).

**When gap exhausted:**
1. Run renormalization pass in background thread
2. Reassign all tiles as 1.0, 2.0, 3.0, ... in current order
3. Sync all to Firestore atomically
4. UI remains responsive (offloaded to WorkManager)

This happens **rarely** in practice. A single workspace with 100 inserts between same 2 tiles before gap exhaustion.

### Drag-to-Reorder Flow

```
USER LONG-PRESSES TILE
  ↓
CALCULATE new orderIndex based on drop position
  ↓
WRITE new orderIndex to Room
  ↓
EMIT updated grid to UI (instant visual feedback)
  ↓
DEBOUNCE 0ms (no debounce for structural changes)
  ↓
PUSH to Firestore
```

**Why no debounce?** Reordering is a structural change, not a text input. One-shot write is safe.

---

## 📁 Project Structure

```
app/
├── :core:data
│   ├── db/              ← Room entities, DAOs, conflict detection
│   ├── repository/      ← Single source of truth
│   └── sync/            ← WorkManager background sync
│
├── :core:domain
│   ├── model/           ← Domain models
│   └── conflict/        ← Conflict detection logic
│
├── :core:network
│   ├── firebase/        ← Firestore, Auth
│   └── cloudinary/      ← Image upload
│
├── :core:ui
│   ├── theme/           ← MaterialTheme
│   ├── components/      ← Shared Composables
│   └── gesture/         ← Reusable gesture modifiers
│
├── :feature:workspace   ← Grid view & reordering
├── :feature:editor      ← Note editing & embedded images
├── :feature:conflict    ← Conflict resolution UI
└── :feature:auth        ← Firebase Auth flow
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog+
- Kotlin 1.9+
- Firebase project with Firestore enabled
- Cloudinary free account (no credit card required)

### Setup

1. **Clone & Open**
   ```
   git clone https://github.com/NiazSagor/NoteTaker.git
   open in Android Studio
   ```

2. **Firebase Setup**
   - Create Firebase project at console.firebase.google.com
   - Enable Firestore and Anonymous Auth
   - Download `google-services.json` → place in `app/`

3. **Cloudinary Setup**
   - Sign up at cloudinary.com (free tier)
   - Create unsigned upload preset
   - Add to `local.properties`:
     ```
     CLOUDINARY_CLOUD_NAME=your_cloud_name
     CLOUDINARY_UPLOAD_PRESET=your_preset
     ```

4. **Build & Run**
   ```
   ./gradlew build
   Run on emulator or device
   ```

---

## 🧪 Testing the Advanced Features

### Test Conflict Detection
1. Open app on Device A & B with same workspace
2. Edit same note on both simultaneously
3. Go Device A offline, make changes
4. Reconnect → conflict dialog appears with diff
5. Try all 3 resolution options

### Test Real-Time Sync
1. Create note on Device A
2. Instantly appears on Device B (if online)
3. Edit on B while A is offline
4. A reconnects → sees changes in real-time

### Test 3-Finger Rotation
1. Add image to note
2. Place 3 fingers on image simultaneously
3. Rotate fingers around center → image rotates
4. Lift any finger → saves rotation
5. Refresh note → rotation persists

### Test Fractional Indexing
1. Create 5 notes
2. Reorder 50+ times between same 2 tiles
3. Export DB to verify no corruption
4. Check Firestore indices are unique

---

## 📚 Key Architecture Decisions

- **Room + Firestore:** Room is always the source of truth. Firestore changes are applied to Room, never read directly.
- **Optimistic Writes:** Changes write to Room immediately, UI updates instantly, then syncs to Firestore.
- **No CRDT:** Manual conflict resolution UI required (not automatic CRDTs). Users always see and choose.
- **Cloudinary over Firebase Storage:** No paid plan required, free 25GB storage + bandwidth.
- **Fractional Indexing:** Scales to unlimited reorders without migration or corruption.
- **3-Finger Gestures:** Unique rotation control that doesn't conflict with system gestures.

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| Changes not syncing | Check network, verify Firestore rules allow read/write |
| Conflict dialog appears repeatedly | Device clocks out of sync, or heavy concurrent editing |
| Images not loading | Verify Cloudinary credentials in local.properties |
| Rotation resets on app restart | SavedStateHandle should persist—check ViewModel scope |
| Grid tiles jumping positions | Fractional index gap exhausted—check WorkManager logs |

---

## 📖 Additional Resources

- [Firestore Best Practices](https://firebase.google.com/docs/firestore/best-practices)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)

---

**NoteTaker v1.0** — Built for real-time collaboration with intelligent conflict resolution.
