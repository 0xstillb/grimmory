# Upstream Sync Policy

This fork tracks upstream on `develop`.

## Git Setup

Enable `rerere` in each local clone before doing upstream sync work:

```bash
git config --local rerere.enabled true
git config --local rerere.autoupdate true
```

`rerere` lets Git remember how we resolved repeated conflicts so the next similar merge is usually auto-resolved.

## Workflow Files With Fork Policy

When merging `upstream/develop` into this fork, keep the fork behavior for these files:

- `.github/workflows/release-candidate.yml`
  - Prefer the fork version.
  - Reason: this fork uses the `develop-*` release channel/versioning flow instead of upstream's nightly flow.

- `.github/workflows/preview-image.yml`
  - Do not revive the upstream active workflow unless we intentionally change policy.
  - Reason: this fork keeps preview image generation disabled and manages that separately.

## Merge Checklist

1. Fetch upstream: `git fetch upstream`
2. Merge into fork develop: `git merge upstream/develop`
3. If the workflow files conflict, keep the fork policy above.
4. Run frontend verification after conflict resolution.
5. If backend tests fail only in the known Windows/native cluster, record that clearly in the sync summary.

## Notes

- This file is documentation only; it does not change Git merge behavior by itself.
- The local `rerere` config is not committed to the repo, so each clone should enable it once.
