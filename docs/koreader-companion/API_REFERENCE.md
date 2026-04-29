# Planned MVP API Reference

This document describes the planned KOReader Companion MVP API surface. The contracts below are planning references, not final implemented behavior.

## Authentication Model

- All `/api/koreader/**` endpoints require KOReader companion authentication.
- Reading-session endpoints require authenticated companion access or an authenticated Grimmory user session, depending on the final security wiring.
- Exact header names may be refined during implementation, but the companion path should use a dedicated auth boundary instead of reusing browser session assumptions.

## GET `/api/koreader/users/auth`

- Purpose: validate KOReader companion credentials and return basic authorization status
- Auth requirement: required
- Request payload: none
- Expected response shape:

```json
{
  "status": "ok",
  "username": "reader-name",
  "userId": 123,
  "syncEnabled": true,
  "syncWithGrimmoryReader": false
}
```

- Phase status: MVP

## GET `/api/koreader/books/by-hash/{bookHash}`

- Purpose: find a Grimmory book record using the KOReader-provided content hash
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookHash`: KOReader-visible file/content hash
- Expected response shape:

```json
{
  "found": true,
  "book": {
    "id": 456,
    "title": "Example Book",
    "hash": "abc123...",
    "coverHash": "def456..."
  }
}
```

- MVP fallback when not found:

```json
{
  "found": false,
  "book": null
}
```

- Phase status: MVP

## GET `/api/koreader/syncs/progress/{bookHash}`

- Purpose: fetch the latest server-side KOReader-native progress state for a matched book
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookHash`: KOReader-visible file/content hash
- Expected response shape:

```json
{
  "found": true,
  "bookId": 456,
  "progress": {
    "percent": 41.2,
    "rawLocation": "koreader-location-token",
    "currentPage": 101,
    "totalPages": 245,
    "device": "KOReader",
    "deviceId": "android-tablet-01",
    "updatedAt": "2026-04-29T12:00:00Z"
  }
}
```

- Phase status: MVP

## PUT `/api/koreader/syncs/progress`

- Purpose: push KOReader-native reading progress to Grimmory
- Auth requirement: required
- Expected request payload:

```json
{
  "bookHash": "abc123...",
  "percent": 41.2,
  "rawLocation": "koreader-location-token",
  "currentPage": 101,
  "totalPages": 245,
  "device": "KOReader",
  "deviceId": "android-tablet-01",
  "clientTimestamp": "2026-04-29T12:00:00Z"
}
```

- Expected response shape:

```json
{
  "status": "stored",
  "bookId": 456,
  "serverTimestamp": "2026-04-29T12:00:01Z",
  "conflict": "none"
}
```

- Notes:
  - KOReader-native progress only
  - no Web Reader bridge
  - no EPUB CFI conversion
- Phase status: MVP

## POST `/api/v1/reading-sessions`

- Purpose: upload a single reading-session event from KOReader
- Auth requirement: required
- Expected request payload:

```json
{
  "bookId": 456,
  "bookType": "EPUB",
  "startTime": "2026-04-29T11:00:00Z",
  "endTime": "2026-04-29T11:15:00Z",
  "durationSeconds": 900,
  "startProgress": 40.0,
  "endProgress": 41.2,
  "progressDelta": 1.2,
  "startLocation": "start-token",
  "endLocation": "end-token",
  "device": "KOReader",
  "deviceId": "android-tablet-01"
}
```

- Expected response shape:

```json
{
  "status": "accepted",
  "sessionId": 789
}
```

- Phase status: MVP

## POST `/api/v1/reading-sessions/batch`

- Purpose: upload offline-queued reading sessions in batch
- Auth requirement: required
- Expected request payload:

```json
{
  "bookId": 456,
  "bookType": "EPUB",
  "sessions": [
    {
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z",
      "durationSeconds": 900,
      "startProgress": 40.0,
      "endProgress": 41.2,
      "progressDelta": 1.2,
      "startLocation": "start-token",
      "endLocation": "end-token"
    }
  ]
}
```

- Expected response shape:

```json
{
  "totalRequested": 1,
  "successCount": 1,
  "results": [
    {
      "sessionId": 789,
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z"
    }
  ]
}
```

- Phase status: MVP

## GET `/api/v1/reading-sessions/book/{bookId}`

- Purpose: fetch a paginated session history for diagnostics, analytics, and future sync tooling
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookId`: Grimmory book ID
- Expected response shape:

```json
{
  "content": [
    {
      "sessionId": 789,
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z",
      "durationSeconds": 900,
      "startProgress": 40.0,
      "endProgress": 41.2,
      "device": "KOReader",
      "deviceId": "android-tablet-01"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

- Phase status: MVP
