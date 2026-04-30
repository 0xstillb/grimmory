# KOReader Companion Architecture

## Intent

Grimmory treats GrimmLink as a companion integration, not a rewrite of the
existing reading stack.

Design goals:

- keep KOReader logic isolated from unrelated Grimmory features
- preserve OPF support on the fork base
- keep KOReader-native progress separate from Web Reader progress
- keep Shelf Sync and annotation sync behind narrow, reviewable seams

## Runtime Boundaries

Current integration boundaries are:

- KOReader controllers under `/api/koreader/**`
- KOReader-oriented services for auth, progress, shelves, annotations, and the Web Reader Bridge
- reading-session endpoints under `/api/v1/reading-sessions/**`

These boundaries let Grimmory serve the plugin without changing core reader behavior.

## Progress Separation

KOReader-native progress remains its own data path.

It preserves:

- raw KOReader `progress`
- raw KOReader `location`
- KOReader xpointer/page data
- device/deviceId
- sync timestamps

The optional Web Reader Bridge uses a separate path:

- native KOReader progress stays in KOReader-native storage
- Web Reader progress stays in existing Web Reader progress storage
- bridge endpoints translate between the two conservatively

This separation prevents a failed or uncertain EPUB CFI conversion from corrupting native KOReader progress.

## Shelf Sync Safety Model

Shelf Sync is intentionally membership-oriented:

- list shelves
- list books in shelf
- download primary book file
- remove shelf membership when explicitly requested

The backend must never use Shelf Sync to:

- delete Grimmory library/server files
- delete Grimmory book records

## Annotation Sync Safety Model

Annotation sync is KOReader-native and separate from Web Reader annotation storage.

Key rules:

- dedupe by stable key
- preserve raw KOReader xpointer/page data
- skip older incoming revisions
- do not write legacy Web Reader annotation fields
- do not require EPUB CFI conversion

## Web Reader Bridge Model

The Web Reader Bridge is optional and default-OFF in the plugin.

Bridge rules:

- bridge calls only the dedicated web-progress endpoints
- native `/syncs/progress` remains authoritative for KOReader-native sync
- EPUB CFI conversion is best-effort only
- failed conversion returns explicit metadata instead of faking an exact position
- conflict decisions remain user-driven in the plugin

## Release-Candidate Safety Invariants

The Prompt 10 release candidate should preserve all of the following:

- OPF support remains intact
- Shelf Sync stays membership-only
- no delete path touches Grimmory library/server files
- no delete path touches Grimmory book records
- native KOReader sync works independently of the Web Reader Bridge
- updater behavior lives in the separate plugin repository and does not modify backend storage rules
