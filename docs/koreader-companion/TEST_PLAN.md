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
