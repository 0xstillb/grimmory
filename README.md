> [!NOTE]
> Grimmory is an independent community fork of Booklore.

<div align="center">

<picture>
  <source srcset="assets/logo-with-text.svg">
  <img src="assets/logo-with-text.svg" alt="Grimmory" height="80" />
</picture>

**Grimmory is a self-hosted digital library for people who take their reading seriously.**

[![Release](https://img.shields.io/github/v/release/0xstillb/grimmory?color=818CF8&style=flat-square&logo=github)](https://github.com/0xstillb/grimmory/releases)
[![License](https://img.shields.io/github/license/0xstillb/grimmory?color=fab005&style=flat-square)](LICENSE)
[![Docker Image](https://img.shields.io/badge/Docker-GHCR-2496ED?style=flat-square&logo=docker&logoColor=white)](https://github.com/0xstillb/grimmory/pkgs/container/grimmory)

[Documentation](https://grimmory.org/docs) · [Quick Start](#quick-start) · [Fork Releases](https://github.com/0xstillb/grimmory/releases)

</div>

---

## Fork Features

This fork extends upstream Grimmory with:

| Feature | Description |
| :--- | :--- |
| **OPF Metadata Import** | Automatically extracts metadata from adjacent `.opf` files during library scans |
| **GrimmLink** | KOReader companion backend — book matching by file hash, reading progress sync, shelf management |
| **PDF Progress Bridge** | Mirrors web reader PDF progress into KOReader sync stream |
| **OPDS Feeds** | Full OPDS catalog for device discovery and book downloads |

---

## Core Features

| Feature | Description |
| :--- | :--- |
| **Smart Shelves** | Custom and dynamic shelves with rule-based filtering, tagging, and full-text search |
| **Metadata Lookup** | Covers, descriptions, reviews, and ratings pulled from Google Books, Open Library, and Amazon, all editable |
| **Built-in Reader** | Read PDFs, EPUBs, and comics in the browser with annotations, highlights, and reading progress tracking |
| **Device Sync** | Connect a Kobo, use any OPDS-compatible app, or sync progress with KOReader |
| **Multi-User** | Separate shelves, progress, and preferences per user with local or OIDC authentication |
| **BookDrop** | Drop files into a watched folder and Grimmory detects, enriches, and queues them for import automatically |
| **One-Click Sharing** | Send any book to a Kindle, an email address, or another user directly from the interface |

### Supported Formats

| Category | Formats |
| :--- | :--- |
| eBooks | EPUB, MOBI, AZW, AZW3, FB2 |
| Documents | PDF |
| Comics | CBZ, CBR, CB7 |
| Audiobooks | M4B, M4A, MP3, OPUS |

---

## Quick Start

Requirements: [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).

### Docker Image Tags

| Tag | Branch | Description |
| :--- | :--- | :--- |
| `latest` | main | Stable release, updated on each published release |
| `v3.0.3-Grimmlink` | main | Pinned stable version |
| `develop` | develop | Latest test build, updated on every push to develop |
| `develop-YYYYMMDD-sha` | develop | Pinned test build for rollback |

```bash
# Stable
docker pull ghcr.io/0xstillb/grimmory:latest

# Test / feature preview
docker pull ghcr.io/0xstillb/grimmory:develop
```

### Step 1: Environment Configuration

Create a `.env` file:

```ini
# Application
APP_USER_ID=1000
APP_GROUP_ID=1000
TZ=Etc/UTC

# Database
DATABASE_URL=jdbc:mariadb://mariadb:3306/grimmory
DB_USER=grimmory
DB_PASSWORD=ChangeMe_Grimmory_2025!

# Optional: enable API docs + export OpenAPI JSON (defaults to false)
API_DOCS_ENABLED=false

# Storage: LOCAL (default) or NETWORK (disables file operations; see Network Storage section)
DISK_TYPE=LOCAL

# MariaDB
DB_USER_ID=1000
DB_GROUP_ID=1000
MYSQL_ROOT_PASSWORD=ChangeMe_MariaDBRoot_2025!
MYSQL_DATABASE=grimmory
```

### Step 2: Docker Compose

Create a `docker-compose.yml`:

```yaml
services:
  grimmory:
    image: ghcr.io/0xstillb/grimmory:latest
    container_name: grimmory
    environment:
      - USER_ID=${APP_USER_ID}
      - GROUP_ID=${APP_GROUP_ID}
      - TZ=${TZ}
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DB_USER}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - API_DOCS_ENABLED=${API_DOCS_ENABLED}
      - DISK_TYPE=${DISK_TYPE}
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - ./books:/books
      - ./bookdrop:/bookdrop
    healthcheck:
      test: wget -q -O - http://localhost:6060/api/v1/healthcheck
      interval: 60s
      retries: 5
      start_period: 60s
      timeout: 10s
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.5
    environment:
      - PUID=${DB_USER_ID}
      - PGID=${DB_GROUP_ID}
      - TZ=${TZ}
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=${MYSQL_DATABASE}
      - MYSQL_USER=${DB_USER}
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - ./mariadb/config:/config
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mariadb-admin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 5s
      retries: 10
```

### Step 3: Launch

```bash
docker compose up -d
```

Open http://localhost:6060, create your admin account, and start building your library. (All libraries must be created within directories mounted on the host, e.g. the `/books/` directory in the sample `docker-compose.yml` above.)

Additional deployment examples:

- Docker Compose: [`deploy/compose/docker-compose.yml`](deploy/compose/docker-compose.yml)
- Helm: [`deploy/helm/grimmory/Chart.yaml`](deploy/helm/grimmory/Chart.yaml)
- Podman Quadlet: [`deploy/podman/quadlet/README.md`](deploy/podman/quadlet/README.md)

---

## GrimmLink API

GrimmLink provides KOReader-compatible endpoints for book matching, progress sync, and shelf management.

### Setup

1. Go to **Settings > KOReader Sync Configuration** in the Grimmory web UI
2. Set **KOReader Username** and **KOReader Password**
3. Enable **KOReader Sync**
4. Enable **Sync reading progress with Grimmory eBook Reader** for PDF Progress Bridge

### KOReader Configuration

In KOReader, configure the sync server:

```
Server: http://<grimmory-host>:6060
Username: <your koreader username>
Password: <your koreader password>
```

### API Endpoints

#### Authentication

| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/koreader/users/auth` | Authenticate KOReader user |

Uses `x-auth-user` and `x-auth-key` (MD5) headers.

#### Progress Sync

| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/koreader/syncs/progress/{bookHash}` | Get reading progress by file hash |
| PUT | `/api/koreader/syncs/progress` | Update reading progress |

#### PDF Progress Bridge

| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/koreader/books/{bookId}/pdf-progress` | Get PDF reading progress |
| PUT | `/api/koreader/books/{bookId}/pdf-progress` | Update PDF reading progress |

Web reader PDF progress is automatically mirrored to KOReader sync, so both clients stay in sync.

#### Book Matching & Downloads

| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/koreader/books/by-hash/{bookHash}` | Find book by file hash |
| GET | `/api/koreader/books/{bookId}/download` | Download book file |

#### Shelf Management

| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/koreader/shelves` | List available shelves (regular + magic, optional `?type=regular|magic`) |
| GET | `/api/koreader/shelves/{shelfId}/books` | List books in shelf |
| GET | `/api/koreader/shelves/{shelfType}/{shelfId}/books` | List books in typed shelf (`regular` or `magic`) |
| POST | `/api/koreader/shelves/{shelfId}/books/{bookId}/remove` | Remove book from shelf |
| POST | `/api/koreader/shelves/{shelfType}/{shelfId}/books/{bookId}/remove` | Remove from typed shelf (`magic` returns unsupported; no file deletion) |

#### Reading Sessions

| Method | Path | Description |
| :--- | :--- | :--- |
| POST | `/api/v1/reading-sessions` | Log a reading session |
| POST | `/api/v1/reading-sessions/batch` | Log batch of reading sessions |

Accepts both Grimmory auth and KOReader `x-auth-user`/`x-auth-key` headers.

---

## OPDS Feeds

OPDS catalog feeds are available for any OPDS-compatible reader app (KOReader, Librera, Moon+ Reader, etc.).

| Path | Description |
| :--- | :--- |
| `/api/v1/opds` | Root catalog |
| `/api/v1/opds/libraries` | Browse by library |
| `/api/v1/opds/shelves` | Browse by shelf |
| `/api/v1/opds/authors` | Browse by author |
| `/api/v1/opds/series` | Browse by series |
| `/api/v1/opds/catalog` | Full book catalog |
| `/api/v1/opds/recent` | Recently added books |
| `/api/v1/opds/{bookId}/download` | Download book |
| `/api/v1/opds/{bookId}/cover` | Book cover image |

---

## API Reference Docs

When enabled via `API_DOCS_ENABLED=true`, full API documentation is available:

- API reference docs: `http://localhost:6060/api/docs`
- OpenAPI JSON: `http://localhost:6060/api/openapi.json`

---

## BookDrop

Drop book files into a watched folder. Grimmory picks them up, pulls metadata, and queues them for your review.

```mermaid
graph LR
    A[Drop Files] --> B[Auto-Detect]
    B --> C[Extract Metadata]
    C --> D[Review and Import]
```

| Step | What Happens |
| --- | --- |
| 1. Watch | Grimmory monitors the BookDrop folder continuously |
| 2. Detect | New files are picked up and parsed automatically |
| 3. Enrich | Metadata is fetched from Google Books and Open Library |
| 4. Import | You review, adjust if needed, and add to your library |

Mount the volume in `docker-compose.yml`:

```yaml
volumes:
  - ./bookdrop:/bookdrop
```

---

## Network Storage

Set `DISK_TYPE=NETWORK` in your `.env` to run Grimmory against a network-mounted file system (NFS, SMB, etc.).
In this mode, direct file operations (delete, move, rename from the UI) are disabled to avoid destructive changes on shared mounts.
All other features — reading, metadata, sync — remain fully functional.

---

## Developer Surfaces

Contributor workflow, PR policy, and release semantics live in [CONTRIBUTING.md](CONTRIBUTING.md).

General purpose development guidelines live in [DEVELOPMENT.md](DEVELOPMENT.md). Component-specific implementation guidance lives in:

- [`backend/DEVELOPMENT.md`](backend/DEVELOPMENT.md)
- [`frontend/DEVELOPMENT.md`](frontend/DEVELOPMENT.md)

The root [`Justfile`](Justfile) is the primary local command surface and mirrors the folder-local `backend/Justfile` and `frontend/Justfile` entrypoints.

```bash
just               # Show root + api + ui recipes
just test          # Run backend and frontend tests
just api test      # Run backend tests only
just ui dev        # Start the frontend dev server
```

---

## Community and Support

| Channel | |
| :--- | :--- |
| Report a bug | [Open an issue](https://github.com/0xstillb/grimmory/issues) |
| Upstream project | [grimmory-tools/grimmory](https://github.com/grimmory-tools/grimmory) |

---

## License

Distributed under the terms of the [AGPL-3.0 License](LICENSE).
