# KOReader Companion Test Plan

## Goals

- validate GrimmLink backend compatibility without regressing OPF support
- prove API compatibility for the current plugin MVP
- verify safety invariants for Shelf Sync, annotation sync, updater boundaries, and the Web Reader Bridge

## Automated Backend Checks

Preferred backend checks:

- `.\gradlew.bat test`
- focused KOReader slices when full-suite failures are known to be unrelated:
  - `KoreaderControllerTest`
  - `ReadingSessionControllerTest`
  - `KoreaderShelfControllerTest`
  - `KoreaderAnnotationControllerTest`
  - `KoreaderWebReaderBridgeControllerTest`
  - matching service tests

Environment note:

- on restricted Windows environments, Gradle test runs may fail while writing temporary files or loading unrelated native dependencies
- if that happens, repeat the failing suite on a normal workstation or CI runner before treating it as a GrimmLink regression

## API Contract Checks

Verify request/response compatibility for:

- `GET /api/koreader/users/auth`
- `GET /api/koreader/books/by-hash/{bookHash}`
- `GET /api/koreader/syncs/progress/{bookHash}`
- `PUT /api/koreader/syncs/progress`
- `POST /api/v1/reading-sessions`
- `POST /api/v1/reading-sessions/batch`
- `GET /api/v1/reading-sessions/book/{bookId}`
- shelf sync endpoints
- annotation/bookmark/rating endpoints
- Web Reader Bridge endpoints

## Security And Safety Checks

- invalid or missing KOReader companion auth returns `401`
- users cannot fetch or mutate books outside accessible libraries
- Shelf Sync remove calls do not delete server-side files
- Shelf Sync remove calls do not delete Grimmory book records
- annotation endpoints do not touch legacy Web Reader annotation tables
- Web Reader Bridge writes do not replace KOReader-native progress storage

## Manual Runtime Checks

Recommended manual checks with a real KOReader device:

1. Install `grimmlink.koplugin`.
2. Configure Grimmory server URL, KOReader username, and `x-auth-key`.
3. Open a matched book.
4. Verify native progress pull.
5. Advance local progress and close/suspend.
6. Verify native progress push or queue behavior.
7. Verify annotation/bookmark/rating sync.
8. Verify Shelf Sync download behavior.
9. Verify Web Reader Bridge remains inactive by default.
10. Enable bridge intentionally and validate prompt-based conflict handling.

## Shelf Sync Checks

- list shelves returns accessible shelves only
- shelf books list exposes file metadata needed by GrimmLink
- tracked download deletion only happens when requested by the plugin
- remove-from-shelf unlinks membership only
- `.sdr` deletion is a plugin-side option, not a backend delete path

## Web Reader Bridge Checks

- `GET /api/koreader/books/{bookId}/web-progress` requires auth and book access
- `PUT /api/koreader/books/{bookId}/web-progress` never deletes files or book records
- `POST /api/koreader/books/{bookId}/cfi/resolve` returns `converted=false` with a reason when conversion is unsafe or unsupported
- failed bridge conversion does not block native KOReader sync
- bridge-disabled plugin behavior still uses KOReader-native sync only

## Known Release-Candidate Limitations

- full backend test runs may still expose unrelated pre-existing failures outside the GrimmLink surface
- KOReader device/runtime behavior still requires validation on real hardware
- EPUB CFI conversion remains best-effort only
