# KOReader Companion Test Plan

## Goals

- Validate KOReader companion behavior without regressing OPF support
- Prove API compatibility before plugin rollout
- Keep KOReader-native EPUB progress separate from Grimmory Web Reader progress

## Backend Tests

- unit tests for KOReader auth parsing and authorization decisions
- unit tests for hash-based book matching
- unit tests for KOReader progress merge/conflict rules
- unit tests for reading-session persistence and batch upload handling
- persistence tests for KOReader-native progress storage boundaries

Environment note:

- on restricted Windows sandbox environments, some Gradle test runs may fail while writing test-result binaries or temporary OPF fixtures even when the GrimmLink code under test is unchanged
- if that happens, rerun the targeted backend and OPF tests on a normal local workstation or CI runner with unrestricted temp/test-results filesystem access before treating it as an application regression

## API Compatibility Tests

- request/response contract tests for planned `/api/koreader/**` endpoints
- request/response contract tests for reading-session endpoints
- auth failure and permission failure coverage
- malformed payload validation tests

## Plugin Tests

- configuration persistence tests
- auth header behavior tests
- book hash lookup behavior tests
- progress push/pull behavior tests
- offline queue enqueue/dequeue/retry tests
- conflict dialog decision tests
- safe jump fallback tests when raw remote location cannot be applied

## Android KOReader Runtime Tests

- best-effort runtime verification on Android KOReader
- validate login/auth flow against Grimmory
- validate book matching and progress sync on real device behavior
- validate offline reading then batch upload recovery

## Fallback Mock Tests

If emulator, ADB, or device access is unavailable:

- use mock server compatibility tests
- replay representative KOReader payload fixtures
- simulate offline queue flushes and conflict scenarios

## OPF Regression Checks

- confirm OPF import support still works on the fork base
- confirm KOReader planning/implementation changes do not alter OPF metadata extraction behavior
- keep explicit tests around adjacent OPF metadata where touched
- if OPF tests fail with filesystem `AccessDeniedException` in a sandboxed environment, repeat them outside the sandbox before concluding that OPF support regressed

## EPUB Reading Progress Checks

- verify KOReader EPUB progress is stored in KOReader-native fields only
- verify percent/raw location/page counts/device/timestamp survive round trips
- verify optional Web Reader bridge writes stay separate from KOReader-native progress storage

## Web Reader Bridge Checks (Prompt 8)

- verify `GET /api/koreader/books/{bookId}/web-progress` requires auth and book access
- verify `PUT /api/koreader/books/{bookId}/web-progress` never deletes files or book records
- verify newer Web Reader progress is not overwritten without a newer timestamp or explicit forced conflict resolution
- verify `POST /api/koreader/books/{bookId}/cfi/resolve` returns `converted=false` with a clear reason when conversion is unsafe or unsupported
- verify raw KOReader location/page/xpointer remains available even when CFI conversion fails
- verify bridge writes do not modify the `koreader_progress` table
- verify bridge-disabled plugin behavior still uses KOReader-native sync only

## Moon+ Reader-Like Sync Scenarios

- local ahead of remote
- remote ahead of local
- both local and remote changed since last sync
- equal progress with differing timestamps
- rapid progress changes below throttling thresholds
- close/suspend event forcing sync
- `Use Local`
- `Use Remote`
- `Ignore`
- remote jump unavailable, page/percentage fallback

## Security Checks

- verify protected endpoints reject missing or invalid companion auth
- verify users cannot fetch or write unrelated book progress
- verify device-oriented endpoints do not leak broader session state

## Offline Queue Checks

- queue growth under no-network conditions
- replay order after reconnect
- duplicate suppression or idempotency checks
- partial batch failure handling

## Plugin Manual Runtime Checks

Plugin repository:

- [0xstillb/grimmlink](https://github.com/0xstillb/grimmlink)

Recommended KOReader checks:

1. Clone or download `grimmlink.koplugin` from the separate GrimmLink repository, then install it into a KOReader plugins directory.
2. Configure Grimmory server URL, KOReader username, and `x-auth-key` value.
3. Open a matched EPUB.
4. Verify GrimmLink can pull remote progress on open.
5. Move local progress forward and close the book.
6. Confirm progress and reading session upload or queue behavior.
7. Reopen the same book and validate local/remote conflict handling.
8. Repeat while offline, then use `Sync Pending Now` after reconnect.

## Manual API Checks

Assumptions:

- Grimmory backend is running locally
- KOReader companion credentials already exist in Grimmory
- replace `BASE_URL`, `USERNAME`, `MD5_KEY`, `BOOK_HASH`, and `BOOK_ID` with real values

### Auth

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/users/auth"
```

Expected:

- `200 OK`
- response includes `status`, `username`, `userId`, and `syncEnabled`

### Auth Failure

Missing headers:

```bash
curl -i \
  "BASE_URL/api/koreader/users/auth"
```

Expected:

- `401 Unauthorized`
- response indicates authentication is required or KOReader headers are missing

Invalid key:

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: WRONG_KEY" \
  "BASE_URL/api/koreader/users/auth"
```

Expected:

- `401 Unauthorized`
- response indicates invalid KOReader credentials

### Book Lookup By Hash

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/books/by-hash/BOOK_HASH"
```

Expected:

- `200 OK` when the hash matches a Grimmory book the user can access
- `404` for unknown hashes
- `403` if the user is authenticated but cannot access the matched book

### Push KOReader Progress

```bash
curl -i -X PUT \
  -H "Content-Type: application/json" \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  -d '{
    "document": "BOOK_HASH",
    "bookHash": "BOOK_HASH",
    "fileFormat": "EPUB",
    "progress": "xp://point(/1/4/2:0)",
    "location": "xp://point(/1/4/2:0)",
    "percentage": 41.2,
    "currentPage": 101,
    "totalPages": 245,
    "device": "KOReader",
    "device_id": "android-tablet-01",
    "timestamp": 1777425600
  }' \
  "BASE_URL/api/koreader/syncs/progress"
```

Expected:

- `200 OK`
- response body contains `status=progress updated`
- no write should appear in Grimmory Web Reader EPUB fields

### Pull KOReader Progress

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/syncs/progress/BOOK_HASH"
```

Expected:

- `200 OK`
- response includes KOReader-native `progress`, `location`, `percentage`, `currentPage`, `totalPages`, `device`, and `device_id`
- if no progress exists yet, response should still be `200` with an empty progress body for that book

### Create Single Reading Session

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  -d '{
    "bookId": BOOK_ID,
    "bookHash": "BOOK_HASH",
    "bookType": "EPUB",
    "startTime": "2026-04-29T10:00:00Z",
    "endTime": "2026-04-29T10:15:00Z",
    "durationSeconds": 900,
    "durationFormatted": "15m",
    "startProgress": 40.0,
    "endProgress": 41.2,
    "progressDelta": 1.2,
    "device": "KOReader",
    "deviceId": "android-tablet-01",
    "startLocation": "xp://point(/1/4/2:0)",
    "endLocation": "xp://point(/1/4/4:0)"
  }' \
  "BASE_URL/api/v1/reading-sessions"
```

Expected:

- `202 Accepted`
- retrying the exact same payload should not create a duplicate session row

### Create Batch Reading Sessions

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  -d '{
    "bookId": BOOK_ID,
    "bookHash": "BOOK_HASH",
    "bookType": "EPUB",
    "device": "KOReader",
    "deviceId": "android-tablet-01",
    "sessions": [
      {
        "startTime": "2026-04-29T10:00:00Z",
        "endTime": "2026-04-29T10:15:00Z",
        "durationSeconds": 900,
        "durationFormatted": "15m",
        "startProgress": 40.0,
        "endProgress": 41.2,
        "progressDelta": 1.2,
        "startLocation": "xp://point(/1/4/2:0)",
        "endLocation": "xp://point(/1/4/4:0)"
      }
    ]
  }' \
  "BASE_URL/api/v1/reading-sessions/batch"
```

Expected:

- `200 OK`
- response includes `totalRequested`, `successCount`, and `results`
- empty batch should return `400`
- batches larger than 500 items should return `400`

### Invalid Batch

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  -d '{
    "bookId": BOOK_ID,
    "bookHash": "BOOK_HASH",
    "bookType": "EPUB",
    "device": "KOReader",
    "deviceId": "android-tablet-01",
    "sessions": []
  }' \
  "BASE_URL/api/v1/reading-sessions/batch"
```

Expected:

- `400 Bad Request`
- response includes validation details for the empty `sessions` list

### User Isolation Check

1. Push progress with `USERNAME_A` for `BOOK_HASH`.
2. Attempt to pull the same hash with `USERNAME_B`.

```bash
curl -i -X PUT \
  -H "Content-Type: application/json" \
  -H "x-auth-user: USERNAME_A" \
  -H "x-auth-key: MD5_KEY_A" \
  -d '{
    "document": "BOOK_HASH",
    "bookHash": "BOOK_HASH",
    "fileFormat": "EPUB",
    "progress": "xp://point(/1/4/2:0)",
    "location": "xp://point(/1/4/2:0)",
    "percentage": 41.2,
    "device": "KOReader",
    "device_id": "android-tablet-01",
    "timestamp": 1777425600
  }' \
  "BASE_URL/api/koreader/syncs/progress"
```

```bash
curl -i \
  -H "x-auth-user: USERNAME_B" \
  -H "x-auth-key: MD5_KEY_B" \
  "BASE_URL/api/koreader/syncs/progress/BOOK_HASH"
```

Expected:

- `USERNAME_B` must not receive `USERNAME_A` progress data
- if `USERNAME_B` can access the same book but has no synced progress, response should be an empty progress payload
- if `USERNAME_B` cannot access the book, response should be `403`

## Shelf Sync Tests

### Backend Unit Tests

- `listShelves_returnsShelfList` — 200 with user shelves + public shelves
- `listShelves_serviceThrowsForbidden_propagates` — 403 on auth failure
- `listShelfBooks_success` — 200 with book summaries including hash
- `listShelfBooks_shelfNotFound_propagates` — 404 for unknown shelf
- `listShelfBooks_forbidden_propagates` — 403 when shelf is private and not owner
- `downloadBook_success` — 200 with octet-stream resource
- `downloadBook_forbidden_propagates` — 403 when user lacks library access
- `downloadBook_notFound_propagates` — 404 for unknown book

### Manual Shelf API Checks

- `removeBookFromShelf_success` â€” 200 with shelf membership removed only
- `removeBookFromShelf_forbidden_propagates` â€” 403 when shelf or book is inaccessible
- `removeBookFromShelf_notFound_propagates` â€” 404 for unknown shelf or book

Assumptions: same as above; replace `SHELF_ID` and `BOOK_ID` with real values.

#### List shelves

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/shelves"
```

Expected:
- `200 OK`
- JSON array; each item has `id`, `name`, `type`, `bookCount`

#### List books in shelf

```bash
curl -i \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/shelves/SHELF_ID/books"
```

Expected:
- `200 OK`
- JSON array; each item has `bookId`, `title`, `fileName`, `fileFormat`, `fileSizeKb`, `bookHash`
- `403` if shelf is private and user does not own it
- `404` if shelf does not exist

#### Download book file

```bash
curl -i -OJ \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/books/BOOK_ID/download"
```

Expected:
- `200 OK`
- `Content-Disposition: attachment; filename="<originalFilename>"`
- response body is the book file binary
- `403` if user does not have library access to the book
- `404` if book does not exist

#### User isolation check for shelf

#### Remove book from shelf

```bash
curl -i -X POST \
  -H "x-auth-user: USERNAME" \
  -H "x-auth-key: MD5_KEY" \
  "BASE_URL/api/koreader/shelves/SHELF_ID/books/BOOK_ID/remove"
```

Expected:
- `200 OK`
- JSON body with `removedFromShelf: true` and `deletedFromLibrary: false`
- the shelf membership is removed only
- public shelves still require owner/admin permission for this mutation
- `403` if shelf or book is inaccessible
- `404` if shelf or book does not exist

1. Create a private shelf for `USERNAME_A`
2. Attempt to list books with `USERNAME_B`

Expected:
- `403 Forbidden` for the private shelf access attempt

#### User isolation check for download

1. Find a book accessible to `USERNAME_A` but not `USERNAME_B` (different library assignments)
2. Attempt download with `USERNAME_B`

Expected:
- `403 Forbidden`

### Plugin Shelf Sync Manual Checks

1. Configure GrimmLink with a valid Grimmory server
2. Open Shelf Sync → Select Shelf — verify the shelf list appears
3. Select a shelf — verify `shelf_id` and `shelf_name` are saved
4. Sync Shelf Now — verify books appear in the download directory
5. Run sync again — verify already-downloaded books are skipped
6. Remove a book from the Grimmory shelf, re-sync — verify the local book is NOT deleted (default OFF)
7. Enable `Delete Removed Books`, re-sync — verify only GrimmLink-tracked files are deleted
8. Enable `Delete .sdr When Removing`, re-sync after removal — verify the `.sdr` sidecar is removed along with the book
9. Simulate server offline — verify sync fails cleanly with an error message, reading is unaffected
10. Verify progress sync still works after shelf sync (open book, push/pull progress)

## Plugin Conflict Walkthrough

Use these runtime checks when KOReader execution is available:

### Remote Newer Than Local

1. Read on device A and push progress.
2. Open the same book on device B with an older local position.
3. Expect GrimmLink to show a prompt before jumping.
4. Choose `Use Remote` and verify the reader moves only if a safe jump path is available.

### Local Newer Than Remote

1. Keep device offline and move local progress forward.
2. Reconnect and open or close the book.
3. Expect GrimmLink to push local KOReader-native progress without prompting.

### Conflict

1. Advance the same book independently on two devices.
2. Open the book where local and remote differ significantly.
3. Expect GrimmLink to show `Use Local`, `Use Remote`, and `Ignore`.
4. Verify each action:
   - `Use Local` pushes local progress
   - `Use Remote` attempts a safe jump
   - `Ignore` keeps both states untouched
### Shelf Delete Policy Update

- Shelf Sync delete behavior is two-way only when explicitly enabled in the plugin
- Remove-from-shelf calls must hit `POST /api/koreader/shelves/{shelfId}/books/{bookId}/remove`
- The backend must never delete the Grimmory book record or server-side file for KOReader shelf removals
- The plugin must never delete user-added files or files outside the GrimmLink download directory
- `.sdr` removal stays optional and default-off

## Annotation / Bookmark / Rating Sync (Prompt 6 / Prompt 7A)

### Backend unit tests

`KoreaderAnnotationControllerTest` (run with
`./gradlew test --tests "org.booklore.controller.KoreaderAnnotationControllerTest"`):

- `listAnnotations_returnsList`
- `listAnnotations_forbiddenPropagates`
- `upsertAnnotations_returnsBatchResult`
- `listBookmarks_returnsList`
- `upsertBookmarks_returnsBatchResult`
- `upsertBookmarks_bookNotFoundPropagates`
- `listAnnotations_sinceUsesIncrementalRepositoryAndReturnsMergeMetadata`
- `listBookmarks_sinceUsesIncrementalRepositoryAndReturnsMergeMetadata`
- `listAnnotations_forbiddenWhenBookIsOutsideAccessibleLibraries`
- `getRating_returnsCurrentValue`
- `updateRating_returnsUpdatedValue`
- `updateRating_invalidValuePropagates`

### Manual checks (curl)

```
# annotations: list
curl -H "x-auth-user: user" -H "x-auth-key: <md5>" \
  http://localhost:6060/api/koreader/books/42/annotations

# annotations: upsert
curl -X POST -H "x-auth-user: user" -H "x-auth-key: <md5>" \
  -H "Content-Type: application/json" \
  -d '[{"dedupeKey":"abc","koreaderPos":"/x[1]","text":"hello","source":"KOREADER"}]' \
  http://localhost:6060/api/koreader/books/42/annotations/batch

# bookmarks: list / upsert (same shape, /bookmarks)

# rating: get / put
curl -H "x-auth-user: user" -H "x-auth-key: <md5>" \
  http://localhost:6060/api/koreader/books/42/rating
curl -X PUT -H "x-auth-user: user" -H "x-auth-key: <md5>" \
  -H "Content-Type: application/json" \
  -d '{"rating": 8}' \
  http://localhost:6060/api/koreader/books/42/rating
```

### Safety invariants to verify

- Sending the same `dedupeKey` twice does NOT create duplicate rows.
- Setting a rating outside `1..10` (other than `null`) returns `400`.
- A user without library access to a book gets `403` on every endpoint.
- `BookEntity` row count for the test book is unchanged before / after upserts.
- The legacy `annotations` and `book_marks` tables are unchanged before / after.
- Setting `koreaderUpdatedAt` to a value older than the stored row keeps the
  stored row (skipped count > 0).
- `GET /annotations?since=...` and `GET /bookmarks?since=...` only return rows
  updated at or after the provided watermark.
- Prompt 7A pull / merge preserves raw `koreaderPos` / `page`, never deletes
  local user annotations, never writes Web Reader fields, and does not require
  EPUB CFI conversion.
- Prompt 8 Web Reader Bridge keeps Web Reader progress optional and default-off
  in the plugin; failed bridge conversion never blocks reading or native sync.
