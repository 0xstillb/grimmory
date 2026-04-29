# KOReader Companion Plugin Scope

## Purpose

Adapt the BookLoreSync plugin model into a Grimmory-branded KOReader Companion flow without copying legacy implementation blindly.

## Adaptation Goals

- Rename BookLore labels and references to Grimmory
- Point configuration and network behavior at Grimmory endpoints
- Keep updater/release metadata under Grimmory control
- Preserve Moon+ Reader-like sync ergonomics where they fit KOReader usage

## Configuration Expectations

- Server URL must be configurable
- Credentials or companion auth tokens must be configurable
- Plugin messaging and labels should refer to Grimmory, not BookLore
- Auto-update must not point to original BookLoreSync releases

## Auth Expectations

- Use dedicated companion auth headers for KOReader sync calls
- Avoid browser-session assumptions
- Keep auth behavior explicit for device/offline use

## Core Sync Scope

- Book hash matching
- Progress push
- Progress pull
- KOReader-native EPUB progress
- Reading-session upload
- Offline queue for deferred sync

## EPUB Progress Scope

- KOReader EPUB progress must remain KOReader-native
- Preserve raw location/progress payloads
- Preserve percent and page counts when available
- Preserve device and timestamp metadata
- Do not bridge KOReader EPUB progress into Grimmory Web Reader fields

## Moon+ Reader-Like Sync Behavior

Planned behavior direction:

- compare local and remote progress snapshots
- prompt on meaningful conflicts
- allow explicit choice instead of silent overwrite

## Conflict Dialog Behavior

Planned choices:

- `Use Local`
- `Use Remote`
- `Ignore`

## Sync Throttling Rules

Planned sync triggers:

- progress changed by at least 1%
- or reading time increased by at least 5 minutes
- or pages changed by at least 5
- or book close/suspend event occurred

## Offline Queue Expectations

- Queue progress/session uploads when network is unavailable
- Retry in-order when connectivity returns
- Avoid duplicate writes when retries happen
- Keep error handling transparent and reversible

## Out Of Scope For Now

- Web Reader bridge
- EPUB CFI conversion
- frontend UI work in Grimmory
- rating sync implementation
- highlights/notes sync implementation
- bookmarks sync implementation
- shelf/library sync implementation
