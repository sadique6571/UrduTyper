# UrduTyper 2.0 migration tracker

## Sources
- Base: https://github.com/iNomNom/UrduTyper_Clean (MIT licensed). Preserve upstream license and attribution before distribution.
- Feature reference: https://github.com/iNomNom/UrduTyper

## Migrated in this draft
- Gradle multi-module setup, Android manifest, selected editor and font management source, core authentication / image use cases, several layout and theme resources.
- Fixed the first-save directory creation bug and matched JPEG encoding to JPG extension in ImageMaker.
- Added Cloudflare Worker/R2 **prototype** with Firebase ID-token verification.

## Incomplete — do NOT treat as a working build
- Most original source, navigation and resource files are still missing, including packaged Urdu font binaries (GitHub connector cannot fetch large binaries).
- TypeActivity referenced by original manifest is absent in source. Must implement or remove/rewire navigation.
- Need import and commit LICENSE from upstream; review licensing of third-party bundled fonts separately.
- Android app is still connected to Firebase Storage. Cloudflare Android HTTP client, autosave, backup queue, restore, conflict policy and secure token refresh are not integrated.
- Text-only bounded undo/redo has been added on `feature/urdu-typer-completion-plan`, with unit tests. This is not yet a full design-history system: font, colour, background and layer changes are not undoable.
- Material 3 redesign, local Room drafts, WorkManager sync and complete editable-project persistence remain pending.
- Need test compilation, CI, Firebase emulator tests, Cloudflare integration tests and user verification.

## Intended architecture
Firebase Auth for email/Google login; private Cloudflare R2 for per-user project JSON and rendered preview assets; Cloudflare Worker verifies Firebase ID token; offline drafts in Room, sync via WorkManager; no R2 API keys in APK.

## Next development stages
1. Complete upstream source/resources/fonts migration and restore a passing Android build.
2. Modernize storage via MediaStore/SAF, stabilize editor, and run unit/instrumentation tests (including the new TextHistory tests).
3. Implement Android cloud client, Room and WorkManager sync plus editable projects and restore.
4. Replace old UI with Material 3 UrduTyper 2.0 home, editor, gallery, template, backup screens.
5. Harden security (quotas, rate limits, content validation, user-owned metadata, abuse controls), deploy Worker, test actual devices.
