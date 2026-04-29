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

## EPUB Reading Progress Checks

- verify KOReader EPUB progress is stored in KOReader-native fields only
- verify percent/raw location/page counts/device/timestamp survive round trips
- verify no write occurs to Grimmory Web Reader progress fields

## Moon+ Reader-Like Sync Scenarios

- local ahead of remote
- remote ahead of local
- equal progress with differing timestamps
- rapid progress changes below throttling thresholds
- close/suspend event forcing sync

## Security Checks

- verify protected endpoints reject missing or invalid companion auth
- verify users cannot fetch or write unrelated book progress
- verify device-oriented endpoints do not leak broader session state

## Offline Queue Checks

- queue growth under no-network conditions
- replay order after reconnect
- duplicate suppression or idempotency checks
- partial batch failure handling

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
