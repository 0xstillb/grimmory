# KOReader Companion Release Checklist

## Release Summary

- Release name:
- Release date:
- Target branch:
- Target tag:

## Included Features

- [ ] Document finalized MVP features
- [ ] Document companion auth behavior
- [ ] Document book hash matching support
- [ ] Document KOReader-native progress sync support
- [ ] Document reading-session support

## Excluded Features

- [ ] Confirm non-MVP features not included
- [ ] Confirm no Web Reader bridge
- [ ] Confirm no EPUB CFI conversion
- [ ] Confirm deferred features such as ratings/highlights/bookmarks/shelves

## Migration Notes

- [ ] Confirm database migration requirements
- [ ] Confirm config changes if any
- [ ] Confirm plugin upgrade notes

## Compatibility Notes

- [ ] Confirm Grimmory backend compatibility version
- [ ] Confirm KOReader/plugin compatibility expectations
- [ ] Confirm OPF support remains intact

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

## Known Limitations

- [ ] Document current limitations
- [ ] Document unsupported reader data types
- [ ] Document platform/runtime caveats

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
