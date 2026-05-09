# Releases

> [!NOTE]
> This page tracks release information for the `0xstillb/grimmory` fork, with a focus on the OPF-enabled preview build.

<div align="center">

## Grimmory Fork Release Guide

**Clean release notes, install paths, and upgrade pointers for the OPF-enabled fork.**

[![Latest Fork Release](https://img.shields.io/github/v/release/0xstillb/grimmory?color=818CF8&style=flat-square&logo=github)](https://github.com/0xstillb/grimmory/releases)
[![Preview Image](https://img.shields.io/badge/Preview%20Image-GHCR-2496ED?style=flat-square&logo=docker&logoColor=white)](https://github.com/0xstillb/grimmory/pkgs/container/grimmory)
[![Source Repo](https://img.shields.io/badge/Repo-0xstillb%2Fgrimmory-111827?style=flat-square&logo=github)](https://github.com/0xstillb/grimmory)

[GitHub Releases](https://github.com/0xstillb/grimmory/releases) | [Package Registry](https://github.com/0xstillb/grimmory/pkgs/container/grimmory) | [README](README.md)

</div>

---

## Release Channels

| Channel | Purpose | Image Tag | Notes |
| :--- | :--- | :--- | :--- |
| Stable | Current stable build | `ghcr.io/0xstillb/grimmory:latest` | Best for general use |
| Preview | Latest `develop` build | `ghcr.io/0xstillb/grimmory:develop` | Best for testing changes before a release |

---

## Current Fork Release

### `v3.0.3-Grimmlink`

This release is the current public release line for the fork.

**Release page**
- [v3.0.3-Grimmlink](https://github.com/0xstillb/grimmory/releases/tag/v3.0.3-Grimmlink)

**Container images**
- Stable moving tag: `ghcr.io/0xstillb/grimmory:latest`
- Preview moving tag: `ghcr.io/0xstillb/grimmory:develop`
- Pinned release tag: `ghcr.io/0xstillb/grimmory:v3.0.3-Grimmlink`

**Why use this build**
- Adjacent `.opf` metadata can be applied during import without patching upstream yourself
- The fork changes were trimmed down to keep future upstream sync work cleaner
- Release and image paths now point to the renamed GitHub owner `0xstillb`
- The moving tags now point at `latest` for stable and `develop` for preview

---

## Included Fork Changes

The preview release currently focuses on one clear goal: making adjacent `.opf` metadata usable in a cleaner, easier-to-maintain fork.

### Highlights

- Adjacent `.opf` metadata support was centralized instead of being spread across multiple processors
- The fork diff was reduced so syncing with upstream is less painful
- Release, README, and image publishing paths were updated to the current GitHub owner
- Preview images are published directly to this fork's GitHub Container Registry namespace

### Scope

This fork is intentionally narrow. It is meant to stay close to upstream Grimmory while preserving the OPF workflow you need.

---

## Install Paths

### Stable Option

Use the stable moving tag:

```yaml
services:
  grimmory:
    image: ghcr.io/0xstillb/grimmory:latest
```

### Preview Option

Use the preview moving tag:

```yaml
services:
  grimmory:
    image: ghcr.io/0xstillb/grimmory:develop
```

### Reproducible Option

Use the pinned tag from the current release:

```yaml
services:
  grimmory:
    image: ghcr.io/0xstillb/grimmory:v3.0.3-Grimmlink
```

### Keep Existing Data

If you are already running Booklore or Grimmory, you can usually keep:

- your mounted `data` volume
- your mounted `books` volume
- your database container and database name
- your host port mapping

In most cases, the only change you need is the `image:` line.

---

## Upgrade Notes

Before moving to a new preview image:

1. Back up your database.
2. Back up your `data` folder if you want a rollback point.
3. Prefer pinned tags if you want exact reproducibility.
4. Run the new image against the same volumes only after confirming the release note you want.

If you are already on `ghcr.io/0xstillb/grimmory:develop`, pulling the latest image and recreating the container is enough for a normal preview upgrade.

---

## Verification Checklist

After updating, check these first:

1. Grimmory starts and reaches the login page.
2. Existing libraries still mount correctly.
3. A new import with a side-by-side `.opf` file populates metadata as expected.
4. The release image shown in your deployment matches the tag you intended to use.

---

## Where To Look Next

- Main install guide: [README.md](README.md)
- Release feed: [GitHub Releases](https://github.com/0xstillb/grimmory/releases)
- Container package page: [GitHub Container Registry](https://github.com/0xstillb/grimmory/pkgs/container/grimmory)
- Upstream project: [grimmory-tools/grimmory](https://github.com/grimmory-tools/grimmory)

---

## Notes For Future Releases

When the next preview is published, update only these parts:

1. `Current Fork Release`
2. the pinned image tag
3. any new fork-specific highlights worth calling out

That keeps this page readable without turning it into a full changelog dump.
