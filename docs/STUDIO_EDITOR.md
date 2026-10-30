# UrduTyper Studio

The Home and editor use a cream/emerald palette. Existing template categories,
gallery templates, Local posters and Cloud backup remain available.

## Editing

- Tap a text/image layer to select it; double-tap text or use Edit to change words.
- Drag a layer to move it. Drag any corner handle to resize. Pinch and twist a
  selected layer to resize/rotate; use Style for exact width, rotation, size,
  opacity, alignment and line spacing.
- Move canvas switches gestures to viewport zoom/pan; Fit resets the viewport.
- Add independent text and image layers. Fonts, colors and effects affect the
  selected text layer. Layers supports ordering, duplicate, lock, hide and delete.
- The font chooser previews the actual bundled/imported font, names the selected
  face, and identifies the Latin-only font. TTF/OTF imports live in private files,
  rather than cache. Font IDs persist through undo, project reload and export.
- Square, portrait, story, landscape and custom document sizes are available.
  Positions, box dimensions and text sizes scale with the document.
- Choose solid/gradient, built-in styles, gallery image or transparent background.
  Gallery imports preserve EXIF orientation and are sampled to 2048 pixels.

## Persistence and export

Designs autosave after edits and flush on leaving/stopping the editor. Home shows
up to eight recent editable designs; the editor's Drafts list includes all of
them. Tap a title to rename; hold a Home draft to delete. Undo/redo includes text,
font IDs, effects, geometry, layer order, visibility, locks, background and size.

New documents use schema v2 in the private `studio-drafts` directory. Imported
assets/fonts are retained separately so deleting a layer or undoing an edit
does not invalidate an older snapshot. Existing flattened poster files remain
available; a flattened image cannot recover its original text layers.

PNG/JPG exports use the same document renderer as the live canvas and thumbnails.
Viewport zoom, selection handles and UI controls never enter exports. PNG can
remove the background while preserving layer opacity. JPG composites transparent
pixels over white. Output width and JPG quality are configurable, with an 8 MP /
4096-pixel-side limit to control memory use. Gallery export uses MediaStore on
Android 10+. Local poster copies and sharing work without broad storage access;
older Android versions can share the local copy to another app. Exported posters
continue through the existing Cloud upload flow. Editable drafts and imported
font files are device-local; Cloud poster backup is not editable-project sync.

## Validation

Per the requested workflow, **do not build or run tests in the cloud checkout**.
Push the complete batch to `feature/studio-editor`. Its push event starts one
`Android build check` Actions run, which runs all debug unit tests and assembles
the APK in the same Gradle invocation. Do not also dispatch a second run.

CI retains the existing Firebase configuration and signing checks. The repository
secret `FIREBASE_GOOGLE_SERVICES_JSON_B64` remains required. CI uploads the APK and
JUnit XML/HTML results. New tests cover independent font persistence, geometry
resizing, full-state undo/redo, delayed-autosave ordering, corrupt drafts, storage
boundaries, and deletion. Robolectric native-graphics tests exercise Urdu font
changes after reload, transparent export, hidden/rotated layers and aspect ratio.

Before release, check the installed APK on an Android device:

1. Open an existing template, select Alvi then Makki, and confirm different Urdu
   shapes. Return Home and reopen the draft; the selected font must remain.
2. Add two text layers with different fonts and an image. Drag, pinch, rotate,
   resize all corners, reorder, lock, hide and duplicate. Undo/redo each change.
3. Zoom/pan the viewport, change document sizes and backgrounds, then Fit. Rotate
   the device and background/reopen the app to verify draft recovery.
4. Export JPG and transparent PNG at different sizes. Compare line breaks,
   transforms, effects and opacity with the canvas; verify Gallery, Local,
   sharing and Cloud backup on an authenticated device.
