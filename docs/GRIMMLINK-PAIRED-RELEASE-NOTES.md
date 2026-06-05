# GrimmLink Paired Release Notes

Date: 2026-06-05

This backend branch must be released together with a GrimmLink plugin build that targets the canonical v1 API island:

```text
/api/grimmlink/v1/**
```

The matching plugin version must state:

```text
This GrimmLink plugin version requires Grimmory fork with /api/grimmlink/v1 support.
```

## Backend Candidate

- Branch: `codex/clean-grimmlink-api-island`
- Tested head: `d7d3f8d8f chore(devex): document opf hook test guard`
- Base: `upstream/develop` at `e83a143c3 chore(repo): migrate from yarn to pnpm (#1598)`
- Canonical route prefix: `/api/grimmlink/v1`

## Plugin Candidate

- Repository: `0xstillb/GrimmLink`
- Branch: `codex/grimmlink-v1-api-cutover`
- Tested head: `d6c8fa2 feat(api): persist metadata sync cursors`
- API prefix is centralized as `/api/grimmlink/v1`.

## Release Contract

The plugin release path must use the GrimmLink v1 API for normal operation:

- `GET /api/grimmlink/v1/auth`
- `GET /api/grimmlink/v1/books/by-hash/{bookHash}`
- `GET /api/grimmlink/v1/syncs/progress/{bookHash}`
- `PUT /api/grimmlink/v1/syncs/progress`
- `POST /api/grimmlink/v1/reading-sessions`
- `POST /api/grimmlink/v1/reading-sessions/batch`
- `POST /api/grimmlink/v1/syncs/metadata`
- `GET /api/grimmlink/v1/syncs/metadata`
- `POST /api/grimmlink/v1/syncs/metadata/batch`
- `GET /api/grimmlink/v1/shelves`
- `GET /api/grimmlink/v1/shelves/{shelfId}/books`
- `GET /api/grimmlink/v1/shelves/{shelfType}/{shelfId}/books`
- `GET /api/grimmlink/v1/books/{bookId}/download`
- `POST /api/grimmlink/v1/shelves/{shelfId}/books/{bookId}/remove`
- `POST /api/grimmlink/v1/shelves/{shelfType}/{shelfId}/books/{bookId}/remove`
- `GET /api/grimmlink/v1/books/read-statuses`
- `PUT /api/grimmlink/v1/books/{bookId}/status`
- `GET /api/grimmlink/v1/books/{bookId}/pdf-progress`
- `PUT /api/grimmlink/v1/books/{bookId}/pdf-progress`

Legacy `/api/koreader/**` and `/api/v1/reading-sessions` routes may remain in the backend or plugin docs as compatibility/reference material, but they are not the primary release contract for the paired GrimmLink plugin release.

## Core Touches

Fork-owned implementation is isolated under `org.booklore.grimmlink` and `org.booklore.opf` with these narrow core hooks:

- `SecurityConfig` registers authentication for `/api/grimmlink/v1/**`.
- `KoreaderProgress` exposes the fields needed by the GrimmLink facade.
- `ReadingSessionEntity` supports the fork-owned reading-session API surface.
- `AbstractFileProcessor` invokes generic `BookScanMetadataAugmenter` implementations after scan metadata extraction.

The OPF re-land is intentionally generic: `AbstractFileProcessor` does not inject an OPF-specific service.

## Validation Snapshot

Backend targeted checks passed:

```text
.\gradlew.bat test --tests org.booklore.opf.* --tests org.booklore.service.fileprocessor.AbstractFileProcessorTest
.\gradlew.bat test --tests org.booklore.opf.* --tests org.booklore.service.fileprocessor.AbstractFileProcessorTest --tests org.booklore.grimmlink.controller.GrimmlinkV1ControllersTest
```

Plugin checks passed on `codex/grimmlink-v1-api-cutover`:

```text
busted.cmd test\api_client_spec.lua test\main_helpers_spec.lua
busted.cmd test
```

Diff guard passed with no suspicious files:

```text
bash tools/fork-diff-guard.sh upstream/develop
```

The wider backend `.\gradlew.bat test` run did not complete cleanly in this Windows environment. It timed out from the Codex tool, and the resulting XML failures are concentrated in pre-existing Windows-sensitive path, symlink, and native EPUB archive tests rather than the GrimmLink or OPF surfaces.

## Known Gaps Before Release

- Manual KOReader device smoke testing has not been run in this session.
- Async curl/wget device download has unit coverage in the plugin branch but still needs a device smoke test.
- The full backend suite should be rerun in the intended CI/Linux environment before merging.
- Do not merge or release the plugin branch before the backend branch with `/api/grimmlink/v1/**` is available.

## Suggested Release Pairing

- Merge backend branch into backend `develop`.
- Merge plugin branch into plugin `main` after backend availability is confirmed.
- Tag the backend and plugin as a paired release.
- Suggested plugin bump: minor version, because the primary API contract changes to `/api/grimmlink/v1/**`.
