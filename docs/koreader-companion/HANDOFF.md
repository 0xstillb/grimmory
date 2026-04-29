# Grimmory KOReader Companion Handoff

## Repository Status

- Repository: `0xstillb/grimmory`
- Default/base branch: `opf-support-upstream`
- Working branch for KOReader Companion planning and implementation: `feature/OPF-KOreader-plugin`
- Current task scope: documentation/bootstrap only
- OPF support exists on the base branch and must remain intact

## Reference Repositories

- Upstream Grimmory: <https://github.com/grimmory-tools/grimmory>
- BookLoreSync plugin reference: <https://github.com/WorldTeacher/BookLoreSync-plugin>
- BookLore KOReader branch reference: <https://github.com/WorldTeacher/booklore/tree/koreader-plugin>

## High-Level Goal

Build an upstream-friendly Grimmory KOReader Companion Plugin integration that supports:

- KOReader authentication
- Book matching by hash
- KOReader-native progress push/pull
- EPUB progress sync for books opened in KOReader
- Reading session tracking
- Offline batch session upload
- Moon+ Reader-like sync behavior
- Future rating, highlight, note, bookmark, and shelf/library sync

## Planned Phases

### Phase 1 MVP Backend

- Define KOReader-specific backend boundaries and DTOs
- Implement auth, book hash lookup, progress push/pull, and reading-session endpoints
- Keep KOReader progress separate from Grimmory Web Reader progress

### Phase 2 Plugin Adaptation With Moon+ Reader-Like Sync

- Adapt BookLoreSync plugin behavior to Grimmory naming and endpoints
- Add server configuration, auth headers, conflict handling, offline queue, and throttled sync behavior

### Phase 3 Reader Data Sync

- Add KOReader-native support for ratings, highlights, notes, and bookmarks
- Preserve additive architecture and avoid web-reader coupling

### Phase 4 Shelf/Library Sync

- Sync shelf/library metadata from Grimmory to KOReader where practical
- Keep server-driven mapping logic isolated from the rest of the app

### Phase 5 Stabilization/Release

- Regression test OPF support and KOReader APIs
- Validate plugin upgrade paths, compatibility, and release packaging

## Explicit Non-Goals

- No Web Reader bridge
- No EPUB CFI conversion
- No writing KOReader progress into Grimmory Web Reader fields
- No frontend UI work for the planning task
- No backend implementation during this documentation task
- No plugin code port during this documentation task
- No rating, highlight, bookmark, shelf, or library implementation yet

## OPF Preservation Notes

- `opf-support-upstream` contains custom OPF import support that must be preserved across all future KOReader work.
- KOReader planning and implementation must not remove or regress adjacent OPF metadata behavior.
- Any future backend changes touching file import, metadata extraction, or book matching should include explicit OPF regression checks.

## EPUB Reading Progress Notes

- EPUB progress in this project means KOReader-native EPUB reading progress only.
- Store raw KOReader location/progress, percentage, page counts when available, device metadata, and sync timestamps.
- Keep KOReader EPUB progress separate from Grimmory Web Reader progress fields and semantics.
- Do not implement EPUB CFI conversion as part of this project scope.

## Reference Snapshot

- Reference-only plugin change materials should live under `docs/koreader-companion/reference/plugin-changes/`.
- Treat those files as inspection material, not as a blind copy source for application code.
