# KOReader Companion Architecture

## Design Goals

- Stay upstream-friendly and future-merge-friendly
- Keep KOReader-specific behavior isolated from Grimmory core logic
- Preserve OPF support on the fork base branch
- Avoid coupling KOReader-native progress with Grimmory Web Reader progress
- Prefer additive backend packages, entities, repositories, DTOs, and services

## Package Direction

Future implementation should prefer isolated structures such as:

- `controller/koreader`
- `service/koreader`
- `dto/koreader`
- `entity/koreader`
- `repository/koreader`

Where current code layout requires edits to existing classes, keep those edits minimal and focused on wiring or compatibility boundaries.

## Compatibility Layer Approach

The KOReader integration should behave like a companion adapter rather than a rewrite of core Grimmory reading systems.

Recommended approach:

1. Keep KOReader request parsing and response shaping in KOReader-specific controllers/DTOs.
2. Route business logic through KOReader services that translate KOReader payloads into Grimmory-compatible operations.
3. Use small adapter boundaries when existing Grimmory services must be consulted for users, books, or metadata.
4. Keep Moon+ Reader-like sync behavior in a KOReader-specific service path rather than spreading logic through unrelated components.

## Progress Model Separation

KOReader-native reading progress must stay separate from Grimmory Web Reader progress.

Store and operate on KOReader fields such as:

- raw location/progress payload
- percentage
- current page and total pages when available
- device and device ID
- last sync timestamp
- push/pull conflict metadata

Do not reuse or overwrite Grimmory Web Reader progress fields such as:

- `epubProgress`
- `epubProgressPercent`
- `epubCfi`
- web-reader last-position fields

## Prompt 8 - Web Reader Bridge

Prompt 8 adds an optional Web Reader Bridge, but it still follows the same
separation rules:

- keep the bridge in a separate code path from native `/syncs/progress`
- treat EPUB CFI conversion as best-effort only
- never fake an exact EPUB CFI when conversion is unreliable
- never overwrite KOReader-native source data with Web Reader fields
- never let a failed conversion block native KOReader sync
- preserve raw KOReader location/page/xpointer even when the bridge is used

Current bridge direction:

- native KOReader progress remains stored in `koreader_progress`
- Web Reader progress remains stored in the existing Web Reader progress tables
- bridge endpoints translate between them conservatively and return explicit
  `conversionStatus` / `conversionConfidence` metadata

Conflict handling stays user-driven:

- KOReader newer: plugin may push to the bridge
- Web Reader newer: plugin prompts before jumping
- both changed: plugin offers `Use KOReader`, `Use Web Reader`, `Ignore`
- uncertain conversion: keep both sides and avoid a silent overwrite

## Database Design Direction

Future persistence should prefer dedicated KOReader storage tables or isolated companion entities over stretching existing web-reader progress records.

Suggested design direction:

- a KOReader user/auth linkage entity or table
- a KOReader progress entity keyed by user, book, and device
- optional KOReader session records for telemetry and offline batch upload
- optional later entities for highlights, notes, bookmarks, ratings, and shelf sync state

This keeps KOReader semantics explicit and avoids hidden coupling with existing reader features.

## Adapter and Service Boundaries

Suggested responsibilities:

- KOReader controller layer
  - auth endpoint handling
  - book hash lookup endpoints
  - progress push/pull endpoints
  - reading-session endpoints
- KOReader service layer
  - request validation
  - conflict resolution rules
  - throttling and sync heuristics
  - translation to/from Grimmory book and user models
- core Grimmory services
  - reused only where existing book lookup, user lookup, or metadata reads already fit

## Merge Safety

To make upstream sync easier:

- avoid broad refactors of existing controller/service code
- avoid changing existing public APIs unless absolutely necessary
- prefer new files over large edits to old ones
- keep KOReader logic behind narrow seams
- document any required touchpoints into existing code before implementation begins

## Reference Materials

- Use `docs/koreader-companion/reference/plugin-changes/` for inspection only.
- Do not copy those files blindly into application code.
- Extract behavior, contracts, and edge cases from the reference material, then implement them in Grimmory’s current structure.
