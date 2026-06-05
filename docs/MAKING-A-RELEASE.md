# Making a Release

This repository now uses `semantic-release` for stable releases.

For this fork, stable tags must stay on the `GrimmLink` line:

- stable tags use `vX.Y.Z-GrimmLink`
- `semantic-release` must never fall back to old plain `vX.Y.Z` tags from earlier fork history
- always preview the next stable release before promoting `develop` to `main`

Stable releases are not created by manually dispatching a "release" workflow. Instead, a stable release is triggered by pushing release-worthy conventional commits to `main`.

## Overview

The stable release flow has two stages:

1. Push or merge release-worthy commits to `main`.
2. Let GitHub Actions compute the version, create the tag, and publish the images.

The relevant workflows are:

- [`.github/workflows/release-main.yml`](../.github/workflows/release-main.yml)
- [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml)
- [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml)

## Prerequisites

Before cutting a stable release, make sure:

- The commits that should be released are already on `main`.
- Those commits follow conventional commit semantics.
- `RELEASE_BOT_TOKEN` is configured if branch protection requires more than the default `github.token`.
- If `DOCKERHUB_REGISTRY` is set, `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` are configured for Docker Hub publishing.

## What Triggers a Stable Release

`semantic-release` runs on every push to `main` and decides whether a new release is needed.

Release behavior is based on commit history since the last stable tag:

- `feat:` triggers a minor release.
- `fix:`, `perf:`, and `refactor:` trigger a patch release.
- `BREAKING CHANGE:` triggers a major release.
- `docs:`, `ci:`, `build:`, `chore:`, `test:`, and `style:` appear in notes but do not trigger a release on their own.

For this fork, the next version is computed from the latest reachable `vX.Y.Z-GrimmLink` tag on `main`.
Plain upstream-style tags such as `v1.2.1` or merged upstream tags such as `v3.2.0` are not the release base for GrimmLink stable publishing.

## Recommended Maintainer Flow

### 1. Preview the next release

Run the `Release - Dry Run Preview` workflow from the Actions tab.

This workflow is defined in [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml) and accepts:

- `ref`
  Default: `develop`

The preview workflow treats the selected `ref` as a candidate promotion onto `main`. It verifies that the selected commit already contains the current `main` history, then runs `semantic-release --dry-run` against that candidate state.

Use it to confirm:

- whether a release will be created,
- what the next version will be,
- that the version stays on the `vX.Y.Z-GrimmLink` line,
- how the release notes will be grouped,
- and which commits are included in the `main..candidate` release range.

### 2. Merge or push the release-worthy change set to `main`

Once the dry run looks correct, merge or push the desired commits to `main`.

That automatically triggers [`.github/workflows/release-main.yml`](../.github/workflows/release-main.yml).

### 3. Let semantic-release do the versioning work

If a release is warranted, `release-main.yml` will:

- run the migration check,
- run the shared test suite,
- compute the next semantic version,
- create the Git tag `vX.Y.Z-GrimmLink`,
- and create a draft GitHub release.

If no release is warranted, the workflow exits without tagging or publishing.

### 4. Let the stable publish job release the artifacts

After the draft release is reviewed and published on GitHub, [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml) runs from the `release: released` event.

That workflow will:

- build the multi-architecture container image,
- publish `grimmory/grimmory:vX.Y.Z-GrimmLink`,
- publish `grimmory/grimmory:latest`,
- publish `ghcr.io/grimmory-tools/grimmory:vX.Y.Z-GrimmLink`,
- publish `ghcr.io/grimmory-tools/grimmory:latest`,
- and upload the stable release artifacts for the published release.

## Develop Builds

Develop builds are separate from stable releases.

They come from `develop` through [`.github/workflows/publish-develop.yml`](../.github/workflows/publish-develop.yml) and publish:

- `develop`
- `develop-YYYYMMDD-<sha>`
- `grimmory-openapi.json`

## Preview Builds

Manual preview builds for PRs or arbitrary refs are separate from stable releases.

Use the preview-image workflow if you want a one-off test image without creating a stable release.

## Notes

- Stable releases are driven by commit history on `main`, not by labels or manual version bump inputs.
- If the preview shows a plain `vX.Y.Z` result, stop and fix the release base before publishing anything.
- If you need to understand why a release did or did not happen, start with the `Release - Dry Run Preview` workflow and then inspect the `semantic-release` output.
