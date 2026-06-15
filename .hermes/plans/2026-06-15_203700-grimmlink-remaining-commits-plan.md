# GrimmLink Remaining Metadata Bridge Work Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Port the remaining useful GrimmLink work from `feature/grimmlink-native-pdf-progress` onto `develop` as two reviewable, upstream-friendlier backend commits.

**Architecture:** Keep all behavior centered in `backend/src/main/java/org/booklore/grimmlink/service/GrimmlinkMetadataService.java`, with only the minimum repository touchpoints needed to bridge existing Grimmory state into the GrimmLink metadata API. Split rating bridging and bookmark bridging into separate commits so each commit has one concern, clean tests, and clear rollback boundaries.

**Tech Stack:** Spring Boot backend, JPA repositories, GrimmLink DTOs/services, JUnit/Mockito behavior tests.

---

## Current Context / Assumptions

- `develop` already contains `d839035db` (`refactor(grimmlink): keep native pdf progress branch upstream-friendly`).
- Remaining useful work from `feature/grimmlink-native-pdf-progress` is in:
  - `1e196028b` — web rating included in metadata pull
  - `12a66b99f` — rating + bookmark bridging together
  - `fc1ad010b` — bookmark upsert follow-up fix
- `1e196028b` cherry-picks cleanly onto current `develop`.
- `12a66b99f` and `fc1ad010b` do not cherry-pick cleanly as standalone commits, but the sequence does apply. This is a signal to rewrite them into cleaner commits instead of replaying history mechanically.
- Upstream-friendly policy in `docs/UPSTREAM-FRIENDLY-MAINTENANCE.md` favors isolated backend changes, small review surfaces, and separation of concerns.

---

## Proposed Commit Plan (2 commits)

### Commit 1 — `fix(grimmlink): bridge web ratings in metadata sync`

**Intent:** Make GrimmLink metadata sync reflect Grimmory Web personal ratings in both directions, without mixing bookmark behavior into the same commit.

**Source material:**
- keep the useful parts of `1e196028b`
- keep only the rating-related parts of `12a66b99f`
- do **not** include bookmark logic here

**Behavior included:**
- Pull current Grimmory Web personal rating into GrimmLink metadata pull responses.
- Accept GrimmLink rating push payloads and persist them into `UserBookProgressEntity.personalRating`.
- Normalize 5-star to 10-point values where needed.
- Preserve existing metadata-item storage behavior for rating payload history/dedupe.

**Files likely to change:**
- Modify: `backend/src/main/java/org/booklore/grimmlink/service/GrimmlinkMetadataService.java`
- Modify: `backend/src/test/java/org/booklore/grimmlink/service/GrimmlinkServicesBehaviorTest.java`
- Possibly inspect only: `backend/src/main/java/org/booklore/repository/UserBookProgressRepository.java`

**Why this should stand alone:**
- Rating bridge is conceptually independent from bookmark bridge.
- It keeps repository coupling limited to `UserBookProgressRepository`.
- It is the lowest-risk useful behavior to merge next.

**Implementation notes:**
- Extract rating handling behind a helper such as `syncRating(...)` instead of inline logic.
- Keep `pullMetadata(...)` readable: synthesize the current web rating item only when rating is requested (or all types are requested).
- Be explicit about rating validation and scale normalization.
- If no reliable `updatedAt` exists for web ratings, document/accept the current cursor limitation in tests rather than burying it.

**Tests / validation:**
- Add/keep behavior test for `pullMetadata_includesCurrentGrimmoryPersonalRating`.
- Add/keep behavior test for `syncMetadata_updatesCurrentGrimmoryPersonalRating`.
- Run targeted test class:
  - `cd backend && ./gradlew test --tests org.booklore.grimmlink.service.GrimmlinkServicesBehaviorTest --no-daemon --parallel --build-cache`
- If targeted tests pass, run broader backend suite when time allows:
  - `cd backend && ./gradlew test --no-daemon --parallel --build-cache`

**Expected commit message:**
- `fix(grimmlink): bridge web ratings in metadata sync`

---

### Commit 2 — `fix(grimmlink): bridge web bookmarks in metadata sync`

**Intent:** Bridge Grimmory Web bookmarks into GrimmLink metadata sync in both directions, with upsert behavior included from day one.

**Source material:**
- bookmark-related parts of `12a66b99f`
- all functional intent of `fc1ad010b`
- do **not** carry over rating logic from `12a66b99f`

**Behavior included:**
- Persist pushed GrimmLink bookmark payloads into Grimmory Web bookmarks.
- Expose current Grimmory Web bookmarks in metadata pull responses.
- Upsert existing bookmark records by page number or CFI/anchor instead of silently skipping repeat pushes.
- Update title/notes/timestamps on re-push.

**Files likely to change:**
- Modify: `backend/src/main/java/org/booklore/grimmlink/service/GrimmlinkMetadataService.java`
- Modify: `backend/src/main/java/org/booklore/repository/BookMarkRepository.java`
- Modify: `backend/src/test/java/org/booklore/grimmlink/service/GrimmlinkServicesBehaviorTest.java`
- Possibly inspect only: `backend/src/main/java/org/booklore/config/BookmarkProperties.java`
- Possibly inspect only: `backend/src/main/java/org/booklore/model/entity/BookMarkEntity.java`

**Why this should be a separate commit:**
- Bookmark bridge adds heavier coupling to existing Grimmory bookmark internals.
- It needs its own tests and its own review discussion.
- `fc1ad010b` proves the first bookmark implementation was incomplete; the rewrite should ship only the corrected version.

**Implementation notes:**
- Add dedicated helper methods, e.g.:
  - `syncBookmark(...)`
  - `saveGrimmoryBookmark(...)`
  - `toGrimmoryBookmarkPullItem(...)`
  - `bookmarkPage(...)`
  - `bookmarkAnchor(...)`
- Repository API should support lookup for upsert, not only duplicate existence checks:
  - `findFirstByPageNumberAndBookIdAndUserId(...)`
  - `findFirstByCfiAndBookIdAndUserId(...)`
- In `saveGrimmoryBookmark(...)`, prefer:
  - find existing bookmark by page or CFI
  - create if absent
  - update mutable fields regardless
- Keep behavior conservative:
  - do not introduce schema changes
  - do not touch controllers/frontend/docs in this commit
  - do not broaden API scope beyond bookmark bridge

**Tests / validation:**
- Add/keep behavior test for `syncMetadata_createsCurrentGrimmoryPdfBookmark`.
- Add/keep behavior test for `pullMetadata_includesCurrentGrimmoryBookmark`.
- Add a regression test for re-pushing the same bookmark with changed title/notes and asserting the existing entity is updated instead of skipped.
- Run targeted test class:
  - `cd backend && ./gradlew test --tests org.booklore.grimmlink.service.GrimmlinkServicesBehaviorTest --no-daemon --parallel --build-cache`
- Then run broader backend suite if targeted tests pass:
  - `cd backend && ./gradlew test --no-daemon --parallel --build-cache`

**Expected commit message:**
- `fix(grimmlink): bridge web bookmarks in metadata sync`

---

## Recommended Execution Order

1. Branch from current `develop`.
2. Implement **Commit 1 (ratings only)**.
3. Run targeted GrimmLink behavior tests.
4. Commit ratings bridge.
5. Implement **Commit 2 (bookmarks only, with upsert included)**.
6. Run targeted GrimmLink behavior tests again.
7. Run broader backend tests.
8. Commit bookmarks bridge.
9. Open review with explicit note that bookmark upsert is intentionally included in the first bookmark commit, not as a follow-up fix.

---

## Suggested Step-by-Step Next Actions

### Phase A — create the working branch
1. Create a fresh branch from `develop`.
   - Suggested name: `feature/grimmlink-metadata-bridge-upstream-friendly`
2. Work in a fresh worktree if you want to keep the existing one untouched.

### Phase B — build Commit 1 (ratings)
1. Start from current `develop`.
2. Port `1e196028b` logic into `GrimmlinkMetadataService`.
3. Port only the **rating** pieces from `12a66b99f`.
4. Ensure no bookmark repository imports/methods land in this commit.
5. Update/add rating-focused tests in `GrimmlinkServicesBehaviorTest`.
6. Run targeted tests.
7. Commit.

### Phase C — build Commit 2 (bookmarks)
1. On top of Commit 1, port bookmark logic from `12a66b99f`.
2. Immediately fold in the `fc1ad010b` upsert behavior.
3. Add repository lookup methods needed for upsert.
4. Add/update bookmark tests, including re-push regression.
5. Run targeted tests.
6. Run broader backend tests.
7. Commit.

### Phase D — pre-review cleanup
1. Confirm changed files are limited to backend service/repository/tests.
2. Ensure no docs/frontend/CI noise slipped in.
3. Review commit boundaries with:
   - `git diff develop...HEAD --stat`
   - `git log --oneline develop..HEAD`
4. Only then open PR or continue with cherry-pick strategy.

---

## Risks / Tradeoffs / Open Questions

### Risk 1: Rating cursor semantics are imperfect
Web ratings are synthesized from `UserBookProgressEntity.personalRating`, which does not obviously carry a dedicated rating-updated timestamp. The implementation may work functionally while being less precise for incremental pull semantics.

**Mitigation:** keep tests explicit about current behavior and avoid pretending the cursor semantics are stronger than they are.

### Risk 2: Bookmark identity may be lossy across formats
Bookmark matching uses page number for PDF or CFI/anchor for reflowable content. Some device payloads may not map perfectly to current web bookmark identity.

**Mitigation:** keep matching logic conservative and cover re-push/update behavior with tests.

### Risk 3: Commit creep
Because `12a66b99f` mixed ratings and bookmarks together, it is easy to accidentally pull bookmark code into the ratings commit.

**Mitigation:** review staged diff before each commit and enforce file/import boundaries.

---

## Quick Verdict

- **Commit 1:** ratings only, safe to carry forward.
- **Commit 2:** bookmarks only, but ship it already corrected with upsert behavior from `fc1ad010b`.
- Do **not** replay `12a66b99f` and `fc1ad010b` verbatim if the goal is a cleaner upstream-friendly history.
