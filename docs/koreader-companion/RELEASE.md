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
- [ ] Shelf Sync list/download/remove-only contract documented
- [ ] Annotation push/pull merge documented
- [ ] Prompt 8 Web Reader Bridge documented as optional and default-OFF
- [ ] Prompt 8 EPUB CFI conversion documented as best-effort and default-OFF

## Excluded Features

- [ ] Confirm non-MVP features not included
- [ ] Confirm deferred features such as Hardcover rating sync
- [ ] Confirm magic shelves not included in Shelf Sync (regular shelves only for now)

## Compatibility Notes

- [ ] Confirm Grimmory backend compatibility version
- [ ] Confirm KOReader/plugin compatibility expectations
- [ ] Confirm OPF support remains intact
- [ ] Confirm plugin remains isolated from Grimmory backend/frontend source

## Security Checklist

- [ ] Review auth headers and token handling
- [ ] Review access control on KOReader endpoints
- [ ] Review logging for secrets or sensitive payloads
- [ ] Confirm no new delete path touches Grimmory library/server files
- [ ] Confirm no new delete path touches Grimmory book records

## Manual Verification Checklist

- [ ] Auth flow works
- [ ] Book hash lookup works
- [ ] Progress push works
- [ ] Progress pull works
- [ ] Reading sessions upload correctly
- [ ] Offline queue replay works
- [ ] Conflict choices behave as expected
- [ ] Native progress still works with Web Reader Bridge disabled
- [ ] Shelf Sync still obeys remove-membership-only delete policy
- [ ] Annotation sync still preserves local user data
- [ ] Web Reader Bridge prompts safely before using newer remote Web Reader progress
- [ ] Failed CFI conversion falls back safely without blocking reading/sync

## Known Limitations

- [ ] Raw remote jump support still depends on KOReader runtime methods available on the device
- [ ] Web Reader Bridge stores Web Reader progress separately, but device-side runtime validation is still required on real KOReader hardware
- [ ] EPUB CFI conversion is best-effort only; percentage fallback is still expected in some books

## Prompt 8 - Web Reader Bridge

- [ ] Keep KOReader-native progress persisted separately in `koreader_progress`
- [ ] Keep Web Reader progress updates isolated to the Web Reader progress tables
- [ ] Keep Web Reader Bridge default OFF in the plugin
- [ ] Keep EPUB CFI conversion default OFF in the plugin
- [ ] Preserve raw KOReader location/page/xpointer even when bridge sync is enabled
- [ ] Never let failed CFI conversion block native KOReader sync

## Shelf Sync Policy

- Shelf Sync remove actions must only unlink shelf membership.
- The backend must never delete Grimmory library records or server-side files for KOReader shelf removals.
- Public shelf visibility does not grant write access; only the shelf owner or an admin may remove shelf membership.
- `two_way_shelf_delete_sync` defaults to OFF.
- `delete_sdr_on_book_delete` defaults to OFF.

## Annotation / Bookmark / Rating Sync (Prompt 6 / Prompt 7A)

- Annotation sync still uses separate `koreader_annotations` / `koreader_bookmarks` storage.
- Annotation sync still preserves raw KOReader xpointer/page without requiring EPUB CFI conversion.
- Prompt 7A still does not write any Web Reader annotation fields.
- Prompt 8 adds Web Reader Bridge for progress only; it does not change annotation storage rules.

## Next Phase Roadmap

- [ ] Prompt 9 Full Integration / Runtime Test
