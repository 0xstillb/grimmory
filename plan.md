# Grimmory Fork Refactor Plan

## Goal

ทำให้ Grimmory fork ดูแลง่ายแบบ `upstream + fork diff` โดยแยกงานของเราเป็น Grimmlink island ให้มากที่สุด ลดการแก้ไฟล์ upstream core และทำให้การ sync upstream รอบถัดไปเป็นงานที่คาดเดาได้

เป้าหมายสุดท้าย:

```text
upstream release
+ GrimmLink package island
+ fork migrations
+ small integration hooks
= Grimmory develop
```

แนวทางที่ต้องการคือคิดเหมือน clone upstream ใหม่ แล้ว re-land GrimmLink เข้าไปอย่างสะอาด ไม่ใช่ค่อย ๆ ปะ logic เพิ่มในไฟล์ upstream เดิม

## Clean Re-Landing Strategy

แนวคิดหลักคือสร้าง GrimmLink ใหม่บนฐาน upstream ที่สะอาด แล้วใช้ fork ปัจจุบันเป็น reference สำหรับ behavior เท่านั้น

ขั้นตอนระดับสูง:

- เริ่มจาก upstream tag/release ล่าสุดเป็นฐานเปรียบเทียบ
- ดึง GrimmLink และ OPF fork work กลับเข้าไปเป็น package/feature islands และ migrations ของ fork
- เพิ่ม integration hooks เท่าที่จำเป็นจริง ๆ
- port tests ตาม behavior ที่ต้อง preserve ทั้ง GrimmLink และ OPF
- เทียบ API/behavior กับ fork ปัจจุบันก่อน merge เข้า `develop`

สิ่งที่ควรหลีกเลี่ยง:

- ย้าย diff เดิมกลับไปทั้งก้อนโดยไม่แยก ownership
- แก้ upstream controller/service เพราะสะดวกเฉพาะหน้า
- rename ของเดิมเยอะจน merge upstream รอบถัดไปยากกว่าเดิม
- ทำให้ plugin cutover หรือ OPF metadata import behavior พังระหว่าง re-land

เหตุผลที่ยังต้องมี hook บางจุด:

- security ต้องรู้จัก route ของ GrimmLink
- reading session ต้องรองรับ KOReader-authenticated upload
- progress bridge ต้องเชื่อม web reader กับ KOReader progress
- database ต้องมี migrations/entities ของ fork
- repository บางจุดต้องมี query สำหรับ book matching และ sync
- OPF import ต้องเชื่อม metadata scan pipeline ในจุดที่ upstream ยอมรับได้

ดังนั้นเป้าหมายไม่ใช่ zero-touch upstream แต่คือ minimal-touch upstream ที่จุดแตะน้อย อ่านง่าย และมี test คุม

## Principles

- ยึด `develop` เป็นฐานงาน fork และ sync upstream ผ่าน branch แยกก่อนเสมอ
- GrimmLink เป็น source of truth ของ fork API ไม่ใช่ plugin compatibility
- ทำ `/api/grimmlink/v1/**` เป็น canonical API ใหม่ และให้ plugin ย้ายมาใช้ path นี้ก่อน release
- หลีกเลี่ยงการ rename service/entity/DTO ชุดใหญ่ในรอบแรก เพราะจะเพิ่ม diff และทำให้ sync upstream ยาก
- อะไรที่เป็นของ fork ให้ย้ายเข้า package หรือ docs ที่บอกชัดว่าเป็น Grimmlink-owned
- อะไรที่จำเป็นต้องแตะ upstream core ให้เหลือเป็น hook points ที่น้อย มีชื่อชัด และมี regression tests
- ไม่ต้องรักษา legacy compatibility ใน clean re-land branch ถ้า backend และ plugin ถูก cutover พร้อมกันก่อน release

## Desired Architecture

```text
org.booklore                  upstream-owned core
org.booklore.grimmlink        fork-owned API, facade, adapters
org.booklore.service.koreader existing implementation retained during transition
org.booklore.model.dto.*      shared DTOs retained unless versioned DTOs are required
```

### Public API Shape

- Canonical route: `/api/grimmlink/v1/**`
- User settings route: `/api/grimmlink/v1/users/**`
- Reading session route: `/api/grimmlink/v1/reading-sessions/**`
- Metadata sync route: `/api/grimmlink/v1/syncs/metadata`

ใน clean re-land branch ให้ backend และ plugin ใช้ GrimmLink v1 โดยตรง ไม่ต้องออกแบบให้ legacy route เป็น path หลักอีกต่อไป

Temporary compatibility routes อนุญาตได้เฉพาะเพื่อช่วยเทียบ behavior ระหว่างพัฒนา แต่ไม่ถือเป็น release contract

## Phase 1: API Island

เพิ่ม controller ใหม่ใน Grimmlink namespace และทำให้เป็นทางเข้าหลักของ plugin

- `GrimmlinkV1SyncController` สำหรับ auth, book matching, progress sync, metadata batch
- `GrimmlinkV1ShelfController` สำหรับ shelf list, shelf books, download, remove
- `GrimmlinkV1BridgeController` สำหรับ PDF/web progress bridge aliases
- `GrimmlinkV1UserController` สำหรับ user profile และ sync settings
- `GrimmlinkV1ReadingSessionController` สำหรับ reading session upload

service/entity เดิมยัง reuse ได้ แต่ public route ที่ plugin ใช้ต้องเป็น `/api/grimmlink/v1/**`

Acceptance criteria:

- `/api/grimmlink/v1/**` ครอบคลุม flow ที่ plugin ต้องใช้ทั้งหมด
- plugin branch ใช้ `/api/grimmlink/v1/**` โดยตรง
- OpenAPI/Docs ระบุชัดว่า GrimmLink v1 คือ canonical path
- legacy route ไม่จำเป็นต้องเป็น release blocker เว้นแต่ยังมี external client ที่ตั้งใจ support

## Phase 2: Auth And Security Boundary

แยก authentication concern ของ Grimmlink ให้ชัดขึ้น โดยลด logic ที่ฝังใน filter กลาง

- รองรับ `/api/grimmlink/v1/**` ด้วย `x-auth-user` และ `x-auth-key`
- ทำให้ missing/invalid headers fail closed สำหรับ Grimmlink API
- แยก route matching constants ไว้ในที่เดียว เพื่อลดการแก้หลายไฟล์ตอนเพิ่ม endpoint

ถ้าทำได้โดย diff ไม่ใหญ่ ให้สร้าง `GrimmlinkRouteProperties` หรือ constants class สำหรับ route prefixes

Acceptance criteria:

- Auth filter tests ครอบคลุม Grimmlink v1 และ reading-session upload
- ไม่มีการเปิด public route โดยไม่ตั้งใจ

## Phase 3: Facade Layer

สร้าง Grimmlink facade เป็น boundary ระหว่าง API ใหม่กับ implementation เดิม

- API controllers เรียก `GrimmlinkSyncFacade`, `GrimmlinkShelfFacade`, `GrimmlinkUserFacade`
- Facade delegate เข้า `KoreaderService`, `KoreaderShelfService`, `KoreaderMetadataService`, `KoreaderUserService`
- ยังไม่ย้าย persistence/entity ในรอบนี้
- ใส่ mapping หรือ adapter เฉพาะเมื่อ v1 ต้องแยก payload จาก implementation เดิมจริง ๆ

เหตุผลคือถ้าอนาคตต้องเปลี่ยนจาก KOReader-shaped model เป็น Grimmlink-shaped model จะเปลี่ยนที่ facade ไม่ต้องเปลี่ยน controller ทุกตัว

Acceptance criteria:

- Controller บางลง
- Service เดิมยังไม่โดน rename ใหญ่
- จุด integration ระหว่าง fork API กับ implementation เดิมนับได้และหาเจอง่าย

## Phase 4: Metadata Pull-Push Sync

ต่อยอด metadata sync จาก upload-only ให้เป็น two-way sync สำหรับ ratings, annotations, และ bookmarks ผ่าน GrimmLink canonical API

สถานะปัจจุบัน:

- `POST /api/koreader/syncs/metadata` รับ push batch แล้ว store ลง `koreader_metadata_items`
- มี dedupe และ content hash แล้ว
- มี entity สำหรับ rating, annotation, bookmark แล้ว
- ยังไม่มี pull query เช่น by user/book/updatedAt/since
- เอกสารปัจจุบันระบุว่า pull-back และ deletion sync ยัง out of scope

API ที่ควรเพิ่มใน `/api/grimmlink/v1/**`:

- `POST /syncs/metadata` สำหรับ push payload เดิมบน GrimmLink v1
- `GET /syncs/metadata?bookId=&bookHash=&bookFileId=&since=&limit=` สำหรับ pull รายการที่เปลี่ยนหลัง cursor/time
- `POST /syncs/metadata/batch` สำหรับ push-pull ใน call เดียว ถ้า client ต้องการลด round trip

หลักการ contract:

- v1 ต้องรองรับ incremental sync ก่อน full bidirectional conflict engine
- server ส่งกลับเฉพาะ metadata ของ user ที่ auth อยู่และหนังสือที่เข้าถึงได้
- rating sync ต้องไม่ overwrite manual rating แบบสุ่ม ให้ใช้ policy เดิมที่ preserve manual rating เมื่อ conflict
- annotations/bookmarks ใช้ `dedupeKey`, `contentHash`, `clientUpdatedAt`, `updatedAt`, และ `syncedAt` เป็นฐาน conflict/cursor
- deletion sync ยังไม่ทำในรอบแรก เว้นแต่เพิ่ม tombstone migration แยกภายหลัง

Implementation:

- เพิ่ม repository query สำหรับ metadata pull เช่น user/book/item type/updatedAt greater than cursor
- เพิ่ม DTO response สำหรับ pulled items โดยแยกจาก upload result ถ้าจำเป็น
- เพิ่ม facade method เช่น `pullMetadata`, `pushMetadata`, `syncMetadataBatch`
- GrimmLink v1 docs ต้องบอกชัดว่า pull-push support อยู่ที่ `/api/grimmlink/v1/**`

Acceptance criteria:

- client push metadata แล้วย้อน pull รายการเดิมกลับมาได้
- `since` cursor คืนเฉพาะรายการใหม่หรือรายการที่ update หลัง cursor
- unauthorized/inaccessible book ไม่ leak metadata
- plugin ใช้ pull-push ผ่าน GrimmLink v1 โดยตรง

## Phase 4.5: Plugin Clean Cutover

แก้ GrimmLink plugin พร้อม backend refactor ก่อน release เพื่อไม่ต้องแบก compatibility path ยาว ๆ

งานฝั่ง plugin:

- เปลี่ยน API base จาก KOReader-shaped path เป็น `/api/grimmlink/v1`
- ย้าย auth, progress, shelves, download, reading sessions, PDF bridge, metadata sync ไปใช้ v1 routes
- ใช้ metadata pull-push v1 เมื่อ backend พร้อม
- ลบหรือซ่อน fallback legacy path ใน release branch ถ้าไม่ต้อง support server เก่า
- เพิ่ม plugin-side smoke tests หรือ manual checklist สำหรับ connect, sync progress, download, shelf browse, metadata push-pull

Acceptance criteria:

- plugin ใช้ backend clean API ได้ครบ flow
- ไม่มี endpoint ใหม่ที่ยังต้องเรียก `/api/koreader/**` เพื่อให้ใช้งานหลักผ่าน
- backend และ plugin ถูก release เป็นคู่เดียวกัน

## Phase 5: Diff Guard

เพิ่มกลไกตรวจว่า fork diff ไม่หลุดกระจายออกนอกพื้นที่ที่ตั้งใจ

แนวทางเริ่มต้น:

- สร้าง script ตรวจ changed files เทียบ upstream merge-base
- Allowlist package ของ Grimmlink, migrations, docs, tests, security hook files ที่จำเป็น
- ถ้ามีไฟล์นอก allowlist ให้ script fail พร้อมรายชื่อไฟล์
- ใช้เป็น manual check ก่อน merge upstream และค่อยยกระดับเป็น CI หลัง pattern นิ่ง

Allowlist เริ่มต้น:

- `backend/src/main/java/org/booklore/grimmlink/**`
- `backend/src/test/java/org/booklore/grimmlink/**`
- `backend/src/main/resources/db/migration/**`
- `docs/koreader-companion/**`
- `docs/GRIMMLINK-BACKEND-CHANGES.md`
- `AGENTS.md`
- security and reading-session hook files ที่จำเป็นต้องแตะจริง

Acceptance criteria:

- sync upstream แล้วรู้ทันทีว่า fork diff หลุดไปแตะ core ตรงไหน
- allowlist มีขนาดเล็กและ review ได้ง่าย

## Phase 6: Reduce Existing Core Touches

ทยอยย้าย logic ที่เป็น fork-only ออกจาก upstream core

ลำดับที่แนะนำ:

- Reading session KOReader auth bridge
- PDF/web reader bridge route handling
- metadata batch sync entrypoint
- shelf route handling
- debug/error handling ที่อยู่ใน controller core

หลักการคืออย่ารื้อทีเดียว ให้ย้ายทีละ surface พร้อม regression tests

Acceptance criteria:

- จำนวนไฟล์ upstream core ที่ fork ต้องแตะลดลง
- endpoint behavior ไม่เปลี่ยน
- test ชี้ได้ว่า Grimmlink path ครอบคลุม flow ที่ย้ายออกจาก core แล้ว

## Phase 7: OPF Clean Re-Landing

OPF เป็น internal metadata work ไม่ใช่ public API contract จึงควร re-land หลัง API island นิ่งแล้ว แต่ต้องอยู่ใน clean re-landing strategy เดียวกัน

### OPF Product Spec

เป้าหมายของ OPF คือช่วย import metadata จากไฟล์ `.opf` ที่อยู่ข้างหนังสือ โดยไม่ทำให้ metadata system ของ Grimmory ทำงานสับสน และไม่ทำให้ upstream metadata changes conflict ง่าย

OPF ต้องเป็น metadata source แบบอ่านอย่างเดียวในรอบแรก:

- อ่าน `.opf` เพื่อเติม metadata ตอน scan/import
- ไม่เขียน `.opf` กลับ
- ไม่ sync OPF ผ่าน GrimmLink API
- ไม่แทนที่ sidecar metadata system ของ Grimmory
- ไม่แก้ EPUB embedded OPF writer ในรอบแรก

### Ownership

OPF code ควรอยู่ในพื้นที่ ownership ชัดเจน เช่น metadata OPF feature package แยก ไม่กระจายอยู่ใน file processor หลายตัว

ส่วนประกอบที่ต้องมี:

- `AdjacentOpfLocator` หาไฟล์ `.opf` ข้างหนังสือ
- `OpfMetadataExtractor` แปลง OPF เป็น `BookMetadata`
- `OpfMetadataAugmenter` หรือ hook equivalent สำหรับเอา metadata ไปเสริม book ตอน scan
- tests แยก locator, extractor, และ integration behavior

### Discovery Rules

การหา adjacent OPF ต้อง deterministic และไม่เดาสุ่มเกินไป

ลำดับหาไฟล์:

1. หนังสือไฟล์เดี่ยว: `<book-stem>.opf`
2. folder-based book/audiobook: `<folder-name>.opf`
3. fallback: `metadata.opf`
4. single `.opf` ใน folder ใช้ได้เฉพาะเมื่อ folder มีหนังสือที่ support แค่หนึ่งรายการ หรือเป็น folder-based item

กฎกันพลาด:

- ถ้า folder มีหลาย book files และมี OPF ที่ match ไม่ชัด ให้ข้าม
- ไม่ scan recursive ในรอบแรก
- ไม่อ่าน OPF นอก folder ของหนังสือ
- path ต้อง normalize และไม่ตาม symlink/relative path ที่พาออกนอก library context
- error จาก OPF ต้องไม่ทำให้ scan หนังสือล้มทั้งเล่ม

### Metadata Precedence

OPF ต้องไม่ขัดกับงานแก้ metadata ของ Grimmory โดยต้องใช้ precedence policy ชัดเจน

รอบแรกให้ใช้ policy นี้:

- OPF ทำงานเฉพาะตอน scan/import หนังสือใหม่ ไม่ทำงานตอน user แก้ metadata เอง
- OPF เติมเฉพาะ field ที่ OPF มีค่า ไม่ clear field ที่ OPF ไม่มี
- OPF ต้อง respect locked fields ถ้า entity มี lock อยู่
- OPF ไม่ overwrite manual/user-updated metadata หลังหนังสือถูกสร้างแล้ว เว้นแต่มี action import OPF โดยตรงในอนาคต
- ถ้ามี upstream embedded metadata และ adjacent OPF พร้อมกัน ให้ adjacent OPF เป็น override เฉพาะตอน initial scan เพราะผู้ใช้ตั้งใจวางไฟล์ข้างหนังสือ
- ถ้ามี sidecar import/export ของ Grimmory ให้ sidecar เป็นระบบแยก: OPF ไม่เขียนทับ sidecar file และ sidecar write-on-scan จะเขียนผลลัพธ์สุดท้ายหลัง metadata settle แล้ว

Field policy:

- Import allowed: title, authors, publisher, published date, description, language, categories, ISBN10, ISBN13, series name, series number
- Import later only after explicit design: subtitle, series total, ratings, reviews, tags, moods, cover, age/content rating, provider IDs
- Never import from OPF in this phase: read progress, shelves, annotations, bookmarks, user rating

### Integration Contract

OPF ต้องเชื่อม metadata scan pipeline ผ่าน hook เดียวที่เล็กที่สุด

แนวที่ต้องการ:

```text
file processor creates BookEntity
metadata scan hook runs registered augmenters
OPF augmenter optionally applies adjacent OPF metadata
metadata match score recalculates once
sidecar write-on-scan runs after final metadata state
```

สิ่งที่ควรหลีกเลี่ยง:

- ฝัง `AdjacentOpfMetadataApplier` ตรงใน `AbstractFileProcessor`
- เพิ่ม optional setter injection เฉพาะ OPF ใน upstream core
- duplicate field-apply logic ที่ไม่ respect lock/replace mode ของ metadata updater
- เอา OPF ไปผูกกับ GrimmLink API หรือ KOReader sync

ถ้า upstream ไม่มี hook แบบนี้ ให้เพิ่ม hook interface กลางที่ generic เช่น `BookScanMetadataAugmenter` แล้วให้ OPF เป็น implementation หนึ่ง แทนการใส่ OPF-specific dependency ใน core

### Conflict With Grimmory Metadata Work

เพื่อไม่ชนงาน metadata ของ Grimmory:

- ห้ามแก้ behavior ของ `BookMetadataUpdater` เว้นแต่จำเป็นและมี test ชัด
- ห้ามเปลี่ยน sidecar import/export contract
- ห้ามเปลี่ยน embedded EPUB/PDF/CBX metadata writer behavior ในรอบ OPF adjacent import
- ห้ามเพิ่ม migration เพื่อ OPF เว้นแต่ต้องเก็บ source/audit จริง ๆ
- OPF apply logic ควร reuse metadata update helper/policy ที่มีอยู่ ถ้าทำได้โดยไม่ลาก dependency ใหญ่
- ถ้าต้องมี policy ต่างจาก updater ให้ document และ test แยกชัดเจน

### Future Options

สิ่งที่เก็บไว้รอบหลัง:

- manual "Import from adjacent OPF" action
- OPF source audit ว่า field ไหนมาจากไฟล์ไหน
- UI setting เปิด/ปิด OPF import ต่อ library
- cover import จาก OPF manifest
- unified OPF locator สำหรับ EPUB extractor/writer/Kobo ถ้ามี bug หรือ duplication เริ่มเจ็บจริง
- OPF export เข้าระบบ sidecar ถ้าตัดสินใจรวม format ในอนาคต

แนวทาง OPF:

- ใช้ upstream เป็นฐาน แล้ว re-land เฉพาะ OPF behavior ที่ต้องการ
- แยก adjacent OPF locator ออกจาก applier ให้ test ได้เดี่ยว ๆ
- ทำ OPF handling ให้ upstream-friendly, metadata-focused, และไม่ผูกกับ Grimmlink API โดยตรง
- เชื่อม metadata scan pipeline ด้วย hook เล็กที่สุด แทนการฝัง logic กระจาย
- ไม่แตะ EPUB writer/Kobo OPF locator ในรอบแรก เว้นแต่มี bug ชัดเจน

Acceptance criteria:

- OPF diff เป็น metadata-focused ไม่กระทบ API
- upstream sync ไม่ต้องตัดสินใจเรื่อง OPF ระหว่าง resolve API conflicts
- adjacent OPF import behavior เดิมยังผ่าน regression tests
- OPF code อยู่ในพื้นที่ ownership ชัดเจน ไม่กระจายไปหลาย service
- locked fields, sidecar write-on-scan, and metadata match score behavior remain predictable
- malformed or ambiguous OPF files do not fail the whole scan

## Sync Workflow After Refactor

workflow ที่ต้องการหลัง refactor:

```text
git fetch upstream
create sync branch from develop
merge upstream tag/release
renumber upstream migrations if needed
run diff guard
run Grimmlink API regression tests
run backend check
merge sync branch into develop
build/publish develop image
```

สิ่งที่ไม่ควรเกิดอีก:

- resolve conflict แล้วเผลอลบ Grimmlink endpoint
- upstream release workflow กลับมาแทน fork develop image workflow
- migration number ชนกัน
- plugin compatibility เป็นตัวบังคับ design ของ backend

## Test Strategy

Targeted tests:

- Grimmlink v1 controller aliases
- GrimmLink v1 API coverage for all plugin flows
- KOReader header auth on both route families
- reading-session batch upload with KOReader auth
- metadata batch sync
- metadata pull-push sync
- shelf list/download/remove
- PDF/web progress bridge

Wider tests:

- `just api test`
- `just api check`

ถ้า frontend ไม่ได้ถูกแก้ ไม่ต้องบังคับ `just ui check` ในงาน backend-only แต่ถ้า OpenAPI/docs UI หรือ frontend settings page ถูกแก้ ให้รัน frontend checks ด้วย

## Branch Plan

- `codex/grimmlink-api-island` สำหรับ Phase 1 ถึง Phase 3
- `codex/grimmlink-metadata-pull-push` สำหรับ Phase 4
- `codex/grimmlink-plugin-cutover` สำหรับ Phase 4.5
- `codex/grimmlink-diff-guard` สำหรับ Phase 5
- `codex/grimmlink-core-touch-reduction` สำหรับ Phase 6
- `codex/opf-upstream-friendly` สำหรับ Phase 7

แต่ละ branch ควร merge เข้า `develop` ทีละก้อนหลัง test ผ่าน ไม่ควรทำทั้งหมดใน branch เดียว

## Migration Policy

- ห้ามแก้ released migration เดิม
- ห้ามใช้เลข migration ที่ upstream ใช้แล้วถ้า fork timeline ไปไกลกว่า
- upstream migration ที่มาทีหลังต้อง renumber ให้เดินหน้าต่อจาก fork
- migration ของ Grimmlink ควรมีชื่อชัด เช่น `V147__Add_grimmlink_...sql`

## Documentation Updates

ต้องอัปเดตเอกสารเหล่านี้เมื่อแต่ละ phase เสร็จ:

- `docs/koreader-companion/README.md`
- `docs/GRIMMLINK-BACKEND-CHANGES.md`
- `AGENTS.md` ถ้ามีกฎ sync ใหม่
- OpenAPI annotations ของ controller ใหม่

## Risks

- เพิ่ม route alias มากเกินไปอาจทำให้ behavior ซ้ำและ test เยอะขึ้น
- ถ้าแยก facade เร็วเกินไปอาจเกิด abstraction ที่ยังไม่จำเป็น
- ถ้า diff guard เข้มเกินไปตอนแรกจะ block งานจริงที่ยังต้องแตะ core
- ถ้า rename DTO/service เร็วเกินไปจะทำให้ upstream sync ยากขึ้น ไม่ควรทำในรอบแรก

## Definition Of Done

- GrimmLink canonical API อยู่ใต้ `/api/grimmlink/v1/**`
- plugin ใช้ GrimmLink canonical API ครบ flow ก่อน release
- fork-owned code มี island ชัดเจน
- core touch points ถูกบันทึกและมี regression tests
- upstream sync workflow มี diff guard ช่วยตรวจ
- งาน OPF ถูกแยกเป็น internal follow-up ไม่ปนกับ API refactor
- metadata pull-push มี canonical GrimmLink v1 contract
