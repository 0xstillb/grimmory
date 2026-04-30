# KOReader Companion Release Checklist

## Release Summary

- Release name:
- Release date:
- Target branch:
- Target tag:

## Included Features

- [ ] GrimmLink plugin package published from the separate `grimmlink` repository
- [ ] Companion auth behavior documented (`x-auth-user` / `x-auth-key`)
- [ ] Book hash matching support documented
- [ ] KOReader-native progress sync support documented
- [ ] Reading-session and batch upload support documented
- [ ] Moon+ Reader-like conflict prompt documented
- [ ] Shelf Sync — list shelves, list shelf books, download books to local KOReader folder
- [ ] Shelf Sync — safe local mapping with `shelf_sync_map` SQLite table
- [ ] Shelf Sync — optional `Delete Removed Books` (default OFF)
- [ ] Shelf Sync — optional `Delete .sdr When Removing` (default OFF)

## Excluded Features

- [ ] Confirm non-MVP features not included
- [ ] Confirm no Web Reader bridge
- [ ] Confirm no EPUB CFI conversion
- [ ] Confirm deferred features such as ratings/highlights/bookmarks
- [ ] Confirm auto-update remains disabled for GrimmLink
- [ ] Confirm magic shelves not included in Shelf Sync (regular shelves only for now)

## Migration Notes

- [ ] Confirm database migration requirements
- [ ] Confirm config changes if any
- [ ] Confirm plugin upgrade notes

## Compatibility Notes

- [ ] Confirm Grimmory backend compatibility version
- [ ] Confirm KOReader/plugin compatibility expectations
- [ ] Confirm OPF support remains intact
- [ ] Confirm plugin remains isolated from Grimmory backend/frontend source

## Security Checklist

- [ ] Review auth headers and token handling
- [ ] Review access control on KOReader endpoints
- [ ] Review logging for secrets or sensitive payloads

## Manual Verification Checklist

- [ ] Auth flow works
- [ ] Book hash lookup works
- [ ] Progress push works
- [ ] Progress pull works
- [ ] Reading sessions upload correctly
- [ ] Offline queue replay works
- [ ] Conflict choices behave as expected
- [ ] Remote jump fallback is safe when raw location cannot be applied
- [ ] Shelf list endpoint returns correct shelves for authenticated user
- [ ] Shelf books endpoint returns correct books with hash/format/size
- [ ] Book download endpoint streams file correctly
- [ ] Download access control prevents cross-user downloads
- [ ] Shelf Sync plugin downloads missing books
- [ ] Shelf Sync skips already-downloaded books
- [ ] Delete Removed Books only removes GrimmLink-tracked files
- [ ] Progress sync still works after shelf sync

## Known Limitations

- [ ] Raw KOReader jump support depends on KOReader runtime APIs available on the device
- [ ] Progress state is stored as latest local/remote snapshot, not a full per-device history log
- [ ] Auto-update is intentionally disabled until a GrimmLink-specific release channel exists
- [ ] Ratings, highlights, bookmarks, shelves, and Web Reader bridge remain later phases

## Rollback Notes

- [ ] Identify rollback branch/tag
- [ ] Identify schema rollback expectations
- [ ] Identify plugin downgrade guidance

## Next Phase Roadmap

- [ ] Ratings sync
- [ ] Highlights/notes sync
- [ ] Bookmarks sync
- [ ] Shelf/library sync
- [ ] Stabilization follow-up items

## Phase 6 - Web Reader Bridge

- [ ] Keep Web Reader bridge work in a later dedicated phase only
- [ ] Treat EPUB CFI conversion as best-effort, not as an MVP dependency
- [ ] Keep KOReader-native progress persisted separately from Web Reader progress
- [ ] Do not mix KOReader-native progress semantics with Grimmory Web Reader semantics
## Shelf Sync Policy

- Shelf Sync remove actions must only unlink shelf membership.
- The backend must never delete Grimmory library records or server-side files for KOReader shelf removals.
- `two_way_shelf_delete_sync` defaults to OFF.
- `delete_sdr_on_book_delete` defaults to OFF.
