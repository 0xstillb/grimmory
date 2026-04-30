# KOReader Companion Release Checklist

## Branch And Scope

- [ ] backend branch is the intended KOReader/GrimmLink branch
- [ ] plugin repository link points to `0xstillb/grimmlink`
- [ ] Prompt 10 scope stays cleanup/release-readiness only
- [ ] OPF support remains intact

## Documentation Gate

- [ ] `API_REFERENCE.md` reflects the active backend contract
- [ ] `ARCHITECTURE.md` reflects current runtime boundaries
- [ ] `TEST_PLAN.md` reflects current backend and device validation expectations
- [ ] `RELEASE.md` reflects the release-candidate checklist

## Safety Gate

- [ ] no new delete path touches Grimmory library/server files
- [ ] no new delete path touches Grimmory book records
- [ ] Shelf Sync remains shelf membership sync only
- [ ] annotation sync remains KOReader-native and separate from Web Reader annotation storage
- [ ] native KOReader progress remains separate from Web Reader progress
- [ ] Web Reader Bridge remains optional and plugin-default OFF
- [ ] EPUB CFI conversion remains plugin-default OFF and best-effort only

## Test Gate

- [ ] `.\gradlew.bat test` attempted and results recorded
- [ ] focused KOReader test slices pass when full-suite failures are unrelated
- [ ] plugin CI status reviewed from the separate GrimmLink repository
- [ ] `git diff --check` passes before release doc commit

## Release Notes

Record any remaining non-GrimmLink blockers here before release:

- unrelated backend test failures
- local Windows/native dependency issues
- device-runtime checks still pending on real KOReader hardware
