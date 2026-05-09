# GrimmLink Stable KOReader Companion

This branch keeps the KOReader backend intentionally small and upstream-friendly.

## Stable Scope

- Connection and authentication
- Book matching by file hash
- KOReader native progress sync
- Reading session upload
- PDF-only web reader bridge
- Shelf sync
- Offline queue support where the backend needs it
- Conflict metadata through timestamps, device, and progress

## What Is Intentionally Out Of Scope

- EPUB Web Reader Bridge
- XPointer to EPUB CFI conversion
- `/api/koreader/books/{bookId}/cfi/resolve`
- `EpubCfiService` in the KOReader companion flow
- Frontend ebook-reader bridge changes
- Automatic jump behavior for EPUB web reading

## Endpoints

### Authentication

- `GET /api/koreader/users/auth`

Uses `x-auth-user` and `x-auth-key` headers. The key is expected to be KOReader-style MD5 auth.

### Book Matching

- `GET /api/koreader/books/by-hash/{bookHash}`

Matches against `BookFileEntity.currentHash` first, then `initialHash`, while enforcing library access.

### Native KOReader Progress

- `GET /api/koreader/syncs/progress/{bookHash}`
- `PUT /api/koreader/syncs/progress`

This is the stable KOReader-to-KOReader resume path. It stores raw KOReader progress and location data without EPUB CFI conversion.

### Reading Sessions

- `POST /api/v1/reading-sessions`
- `POST /api/v1/reading-sessions/batch`

These are history/statistics events only. They do not depend on the web reader bridge.
For GrimmLink stable support, these upload endpoints accept either the normal Grimmory authenticated user context or KOReader `x-auth-user` / `x-auth-key` headers.

### PDF Bridge

- `GET /api/koreader/books/{bookId}/pdf-progress`
- `PUT /api/koreader/books/{bookId}/pdf-progress`

Compatibility aliases are also available:

- `GET /api/koreader/books/{bookId}/web-progress`
- `PUT /api/koreader/books/{bookId}/web-progress`

The bridge is page-based only and rejects non-PDF formats.

### Shelves

- `GET /api/koreader/shelves`
- `GET /api/koreader/shelves/{shelfId}/books`
- `GET /api/koreader/books/{bookId}/download`
- `POST /api/koreader/shelves/{shelfId}/books/{bookId}/remove`

Shelf removal only affects membership. It does not delete server files or book records.

## Notes

- `/api/koreader/**` requires KOReader headers and fails closed on missing or invalid auth.
- Reading session uploads should use `POST /api/v1/reading-sessions` and `POST /api/v1/reading-sessions/batch`; the KOReader companion client may authenticate these calls with `x-auth-user` / `x-auth-key`.
- Native KOReader progress is the canonical stable sync path for EPUB, PDF, CBX, and other KOReader-supported types.
- PDF progress uses page numbers only and stores raw client timestamps for conflict handling.
