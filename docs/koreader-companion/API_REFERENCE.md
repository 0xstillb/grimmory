# GrimmLink Backend API Reference

This document describes the active GrimmLink <-> Grimmory backend contract.

Plugin repository:

- [0xstillb/grimmlink](https://github.com/0xstillb/grimmlink)

## Authentication

- All `/api/koreader/**` endpoints require `x-auth-user` and `x-auth-key`.
- `/api/v1/reading-sessions/**` accepts the same KOReader companion auth.
- Invalid or missing companion auth returns `401 Unauthorized`.
- Auth failures currently come from servlet `sendError(...)`, so callers must not assume a JSON error body.

## Core KOReader Endpoints

### `GET /api/koreader/users/auth`

- Purpose: validate KOReader companion credentials.
- GrimmLink uses it for **Test Connection** and as a lightweight preflight.
- Response includes:
  - `status`
  - `username`
  - `userId`
  - `syncEnabled`
  - `syncWithGrimmoryReader`

### `GET /api/koreader/books/by-hash/{bookHash}`

- Purpose: match a local KOReader file to a Grimmory book by hash.
- Common plugin fields used from the response:
  - `id`
  - `title`
  - descriptive metadata when present
- Failure behavior:
  - `404 Not Found` for unknown hashes
  - `403 Forbidden` if the user cannot access the matched book

### `GET /api/koreader/syncs/progress/{bookHash}`

- Purpose: fetch the latest KOReader-native progress for a matched book.
- Response may include:
  - `progress`
  - `location`
  - `percentage`
  - `currentPage`
  - `totalPages`
  - `device`
  - `deviceId`
  - `timestamp`
- If the book is known but no progress has been stored yet, the endpoint still returns `200 OK` with an empty progress payload for that book.

### `PUT /api/koreader/syncs/progress`

- Purpose: store KOReader-native progress.
- Expected request fields:
  - `document`
  - `bookHash`
  - `fileFormat`
  - `progress`
  - `location`
  - `percentage`
  - `currentPage`
  - `totalPages`
  - `device`
  - `deviceId`
  - `timestamp`
- Notes:
  - `percentage` is normalized to `0..100`
  - values in the `0..1` range are accepted and converted
  - this endpoint is for KOReader-native progress only

## Reading Session Endpoints

### `POST /api/v1/reading-sessions`

- Purpose: upload a single reading session.
- Expected behavior:
  - `202 Accepted`
  - empty body
- GrimmLink resolves and caches a Grimmory `bookId` before calling this endpoint.

### `POST /api/v1/reading-sessions/batch`

- Purpose: upload offline-queued reading sessions.
- Response includes:
  - `totalRequested`
  - `successCount`
  - `results`
- Validation expectations:
  - empty `sessions` list -> `400`
  - oversized batches -> `400`

### `GET /api/v1/reading-sessions/book/{bookId}`

- Purpose: fetch session history for diagnostics and future tooling.
- GrimmLink MVP does not currently depend on this endpoint at runtime.

## Shelf Sync Endpoints

### `GET /api/koreader/shelves`

- Returns shelves accessible to the authenticated user.
- Includes personal shelves and public shelves the user can see.

### `GET /api/koreader/shelves/{shelfId}/books`

- Returns the books in a shelf accessible to the authenticated user.
- Response fields include:
  - `bookId`
  - `title`
  - `author`
  - `fileName`
  - `fileFormat`
  - `fileSizeKb`
  - `bookHash`

### `GET /api/koreader/books/{bookId}/download`

- Streams the primary book file.
- Security boundary:
  - user must have library access
  - path traversal is blocked by backend file-path validation

### `POST /api/koreader/shelves/{shelfId}/books/{bookId}/remove`

- Removes shelf membership only.
- It does **not**:
  - delete the Grimmory book record
  - delete the Grimmory server-side file
- Mutation still requires the shelf owner or an admin; public shelf visibility alone is not enough.

## Annotation / Bookmark / Rating Endpoints

These endpoints back GrimmLink's KOReader-native annotation sync.

- `GET /api/koreader/books/{bookId}/annotations`
- `POST /api/koreader/books/{bookId}/annotations/batch`
- `GET /api/koreader/books/{bookId}/bookmarks`
- `POST /api/koreader/books/{bookId}/bookmarks/batch`
- `GET /api/koreader/books/{bookId}/rating`
- `PUT /api/koreader/books/{bookId}/rating`

Behavior guarantees:

- dedupe by stable key
- older incoming annotation/bookmark revisions are skipped
- raw KOReader xpointer/page data is preserved
- legacy Web Reader annotation tables are not modified

## Web Reader Bridge Endpoints

The Web Reader Bridge is optional and stays separate from native KOReader sync.

### `GET /api/koreader/books/{bookId}/web-progress`

- Returns bridge-facing Web Reader progress data plus conversion metadata.

### `PUT /api/koreader/books/{bookId}/web-progress`

- Updates Web Reader progress fields only.
- Must not overwrite newer Web Reader progress unless the request is newer or explicitly forced after a user decision.

### `POST /api/koreader/books/{bookId}/cfi/resolve`

- Provides best-effort conversion between Web Reader EPUB CFI and KOReader raw location/xpointer data.
- Unsafe or unsupported conversions return `converted=false` with a reason string.

## Safety Summary

The GrimmLink backend contract must preserve these invariants:

- no Grimmory library/server file delete path
- no Grimmory book record delete path
- Shelf Sync remove calls only unlink shelf membership
- KOReader-native progress stays separate from Web Reader progress
- Web Reader Bridge failures never block native KOReader sync
- raw KOReader location/progress/xpointer remains preserved even when bridge conversion fails
