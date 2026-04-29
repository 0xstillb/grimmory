# KOReader Companion Plugin Scope

## Implementation Strategy

- The KOReader plugin code now lives in [plugins/grimmlink.koplugin](C:/Users/x_boa/Documents/New%20project/grimmory/plugins/grimmlink.koplugin)
- We intentionally keep plugin code isolated from Grimmory backend/frontend source trees
- The implementation reuses ideas from BookLoreSync, but does not vendor the whole legacy plugin blindly
- The shipped scope is MVP-focused: auth, hash match, progress sync, reading sessions, offline queue, and local/remote progress conflict handling

## Branding

- Plugin display name: `GrimmLink`
- Subtitle/description: `KOReader Companion for Grimmory`
- User-facing labels should use `GrimmLink`, `Grimmory server`, `Grimmory sync`, and `KOReader Companion`
- Old `Booklore` and `BookLoreSync` labels should not appear in menus or primary user-facing status messages

## Configuration Surface

The GrimmLink MVP plugin supports:

- Grimmory server URL
- KOReader username
- auth key / password hash used as `x-auth-key`
- device name
- device ID
- auto pull on book open
- auto push on book close
- offline queue enabled
- debug logging
- sync thresholds for percent, minutes, and pages
- manual `Sync Pending Now`

## Auth Model

- GrimmLink uses `x-auth-user` and `x-auth-key`
- The plugin sends the configured `auth_key` value directly and does not transform it at runtime
- The plugin must not log raw auth key values
- `Test Connection` validates against `GET /api/koreader/users/auth`
- Auth failure or server-offline conditions must not block reading

## Core Sync Scope

Implemented / prepared plugin behavior for MVP:

- book matching by hash via `GET /api/koreader/books/by-hash/{bookHash}`
- KOReader-native progress pull via `GET /api/koreader/syncs/progress/{bookHash}`
- KOReader-native progress push via `PUT /api/koreader/syncs/progress`
- reading session upload via `POST /api/v1/reading-sessions`
- batch pending session upload via `POST /api/v1/reading-sessions/batch`
- local SQLite-backed cache for book matches, progress state, pending progress, and pending sessions

## EPUB Progress Scope

- EPUB progress remains KOReader-native
- GrimmLink preserves raw KOReader `progress` / `location`
- GrimmLink preserves `percentage`
- GrimmLink preserves `currentPage` / `totalPages` when KOReader exposes them
- GrimmLink preserves `device`, `deviceId`, and `timestamp`
- GrimmLink does not convert EPUB position to EPUB CFI
- GrimmLink does not bridge KOReader progress into Grimmory Web Reader fields

## Moon+ Reader-Like Sync Behavior

On book open, GrimmLink:

1. computes the local book hash
2. resolves the Grimmory book by hash when possible
3. reads the current local KOReader progress snapshot
4. fetches the latest remote KOReader progress from Grimmory
5. compares local vs remote against cached prior state

Decision behavior:

- `Local newer`: keep KOReader position and push local progress to Grimmory
- `Remote newer`: show a prompt before jumping
- `Conflict`: show a three-way choice dialog
- `Same / insignificant`: do nothing

## Conflict Dialog Behavior

The GrimmLink conflict dialog presents:

- `Use Local`
- `Use Remote`
- `Ignore`

Expected outcomes:

- `Use Local`: keep current KOReader position and push local progress
- `Use Remote`: attempt a safe jump using raw location first, then page/percentage fallback
- `Ignore`: keep both states unchanged for now

If GrimmLink cannot jump safely to the remote location, it must not corrupt the local reading position.

## Sync Throttling Rules

Default sync triggers:

- progress changed by at least `1%`
- or elapsed reading time reached at least `5 minutes`
- or pages changed by at least `5`
- or explicit close / suspend / manual sync event occurred

## Offline Queue Behavior

- pending progress is stored locally and upserted per book hash
- pending sessions are stored locally with duplicate-resistant keys
- retries happen later when the user manually syncs or connectivity is available
- reading continues even if sync fails

## Auto-Update Handling

- Auto-update is disabled for the GrimmLink MVP
- GrimmLink must not point updater behavior at the original BookLoreSync release channel
- A future GrimmLink-specific release repository can re-enable update checks later

## Out Of Scope For This Phase

- Web Reader bridge
- EPUB CFI conversion
- rating sync
- highlights / notes sync
- bookmarks sync
- shelf / library sync
- frontend UI work in Grimmory
- any plugin auto-update channel backed by the original BookLoreSync project
