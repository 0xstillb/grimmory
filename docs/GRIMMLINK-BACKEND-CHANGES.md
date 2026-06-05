# GrimmLink Backend Changes

This fork keeps GrimmLink-owned KOReader integration behind the canonical v1 API island:

```text
/api/grimmlink/v1/**
```

The v1 island is the release-facing contract for the GrimmLink plugin. Legacy KOReader-shaped routes should not be the primary plugin target for new release work.

## Metadata Pull-Push Contract

Metadata sync supports incremental pull-push for ratings, annotations, and bookmarks.

### Push Metadata

```http
POST /api/grimmlink/v1/syncs/metadata
```

Accepts the existing metadata sync payload and stores items in `grimmlink_metadata_items` using:

- `user_id`
- `book_id`
- `book_file_id`
- `item_type`
- `dedupe_key`
- `content_hash`
- `client_updated_at`
- `updated_at`

Items are deduped by `(user_id, book_id, item_type, dedupe_key)`.

### Pull Metadata

```http
GET /api/grimmlink/v1/syncs/metadata?bookId=&bookHash=&bookFileId=&since=&limit=&type=
```

Returns metadata items for the authenticated user and the resolved accessible book only.

- `bookId`, `bookHash`, or `bookFileId` is required.
- `since` is an optional cursor; only items with `updatedAt > since` are returned.
- `limit` defaults to `100` and is clamped to `1..500`.
- `type` optionally filters to `rating`, `annotation`, or `bookmark`.
- `nextCursor` is the newest returned item `updatedAt`, or the supplied `since` when there are no items.

### Batch Push-Pull

```http
POST /api/grimmlink/v1/syncs/metadata/batch
```

Uses the same payload as metadata push, with optional `since`, `limit`, and `type` fields. The server pushes the client payload first, then returns a pull response in the same request.

Deletion sync is intentionally out of scope for this first incremental contract; tombstones should be added in a separate migration if needed.
