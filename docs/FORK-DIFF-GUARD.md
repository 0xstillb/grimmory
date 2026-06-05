# Fork Diff Guard

The fork diff guard is a report-only check for keeping Grimmory changes inside
documented fork ownership areas while the clean re-landing work is still in
progress.

Run it from the repository root:

```bash
tools/fork-diff-guard.sh upstream/develop
```

or through the root command surface:

```bash
just diff-guard upstream/develop
```

## What It Reports

- Allowed changed files.
- Suspicious changed files.
- Core files touched.
- Suggested action.

The default mode is report-only and exits successfully even when suspicious
files are found. This keeps the check useful during the refactor without
blocking work that still needs review.

## Current Allowlist

- `backend/src/main/java/org/booklore/grimmlink/**`
- `backend/src/test/java/org/booklore/grimmlink/**`
- `backend/src/main/java/org/booklore/opf/**`
- `backend/src/test/java/org/booklore/opf/**`
- `backend/src/main/resources/db/migration/**`
- `docs/koreader-companion/**`
- `docs/GRIMMLINK-BACKEND-CHANGES.md`
- `docs/FORK-DIFF-GUARD.md`
- `tools/fork-diff-guard.sh`
- `AGENTS.md`
- `Justfile`

The guard also allows the small documented hook files currently needed by the
GrimmLink API island:

- `backend/src/main/java/org/booklore/config/security/SecurityConfig.java`
- `backend/src/main/java/org/booklore/service/fileprocessor/AbstractFileProcessor.java`
- `backend/src/main/java/org/booklore/model/dto/progress/KoreaderProgress.java`
- `backend/src/main/java/org/booklore/model/entity/ReadingSessionEntity.java`

## Strict Mode

Strict mode is available for future CI hardening:

```bash
tools/fork-diff-guard.sh --strict upstream/develop
```

or:

```bash
just diff-guard-strict upstream/develop
```

In strict mode, the script exits non-zero when suspicious files are present.
Keep CI report-only until the allowlist has settled and the remaining core
touches have been reviewed.
