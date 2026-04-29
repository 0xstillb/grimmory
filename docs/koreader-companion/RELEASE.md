# KOReader Companion Release Checklist

## Release Summary

- Release name:
- Release date:
- Target branch:
- Target tag:

## Included Features

- [ ] GrimmLink plugin package included at `plugins/grimmlink.koplugin`
- [ ] Companion auth behavior documented (`x-auth-user` / `x-auth-key`)
- [ ] Book hash matching support documented
- [ ] KOReader-native progress sync support documented
- [ ] Reading-session and batch upload support documented
- [ ] Moon+ Reader-like conflict prompt documented

## Excluded Features

- [ ] Confirm non-MVP features not included
- [ ] Confirm no Web Reader bridge
- [ ] Confirm no EPUB CFI conversion
- [ ] Confirm deferred features such as ratings/highlights/bookmarks/shelves
- [ ] Confirm auto-update remains disabled for GrimmLink MVP

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
