# Offline word sync (1.3.0)

## Use

1. Add/record/import a voice track in Video → Audio. Select an unlocked visible text layer.
2. Open Video → Word sync (or Audio/Animate → Offline word sync).
3. In Model, choose multilingual Tiny (75 MiB), Base (142 MiB) or Small (466 MiB). Download once, or import an original `ggml-tiny.bin`, `ggml-base.bin` or `ggml-small.bin`. English-only `.en` models and unsupported files are rejected. Downloads/imports are checksum-verified, cancellable and atomically installed. Remove a model to reclaim storage; saved cue playback does not require it.
4. In Analyse, choose Urdu/English/Auto. Align written text keeps the script and matches its words to recognized timestamps. Voice → new text builds a transcript to replace the selected layer only after Apply. Recognition is on-device; audio is not uploaded and no API key is required. The model download itself needs internet.
5. Review: reveal words, highlight, or type each word. Edit/correct the complete text and click Update word list to add/remove/correct words. Exact normalized matches keep timestamps; fuzzy or low-confidence matches are marked. Unmatched words have blank times and cannot be applied until corrected.
6. Adjust each word's start/end in seconds on the complete video timeline. Listen starts preview near that word with audio. Word cards are paginated. Footer Preview uses the same animation renderer as MP4/GIF. Apply records a single undo edit; Cancel restores the project. Clear saved word sync removes cues after Apply.

## Limits and behaviour

- At most 60 seconds of selected voice, 2,000 characters / 600 target words. Device speed, RAM, language, pronunciation, noise/music and Whisper's experimental timestamps affect results. This is not guaranteed phoneme-level forced alignment. Review Urdu poetry on a real recording before relying on timing.
- Matching uses canonical Arabic/Urdu Yeh/Kaaf forms and strips diacritics/punctuation for comparison, preserving the script's original spelling, spaces and line breaks. Fuzzy matches are explicitly uncertain; missing timestamps are never silently fabricated.
- Cues are global movie times with original UTF-16 word bounds. The shared animation renderer accounts for each scene offset. It reveals words at their start, holds text through pauses and highlights only the active cue. Optional typing shapes complete Unicode clusters.
- Source identity, file path, trim and start offset are fingerprinted. Changing voice/trim/offset pauses saved word sync and shows a re-align/clear notice. Volume/fade changes do not invalidate timing. Editing text through the ordinary text editor clears its old cues. Word-sync text correction can preserve matching times. Changing to non-word effects clears cues. Selected duration-fit preserves cues; multi-scene estimated repacing requires clearing cues first so it cannot silently move synced narration to the wrong scenes.
- The current scene can extend within its remaining 60-second budget; matching words from an earlier scene is rejected. Select the correct scene and align its excerpt. Existing unrelated layers/audio/settings remain intact.
- Tiny/Base are lighter choices. Small needs more RAM/time. A conservative free-memory check runs before native model loading. Analysis and model work run off the UI thread, with progress, cooperative cancellation and native cleanup. Leaving the editor cancels active work. There is no automatic paid/server fallback.
- Model operations preserve an installed model on failed/cancelled replacement and remove incomplete files. Native ASR accepts only local audio and known verified models. Audio is decoded to mono 16 kHz from its selected trim. Model weights are excluded from the application APK and git source.

## Native dependency / verification

whisper.cpp v1.9.5, immutable source commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`; CPU-only JNI build, no Curl/server code. Android NDK 26.3.11579264, CMake 3.22.1, arm64-v8a / armeabi-v7a / x86_64. License notices are packaged in `assets/offline-speech-licenses.txt`.

Model SHA-1 values are pinned from upstream `models/README.md`. Downloaded test weights are cached outside source; a verified multilingual Tiny model and upstream JFK recording are generated test-only assets. The device smoke test runs real native recognition under a Java network-forbidden thread policy and verifies words/timestamps and changing cue frames. Urdu matching/persistence/render rules have regression tests; recognition quality on actual Urdu recordings still needs phone QA. A second native UI test exercises correction of a missing word, timing edits, preview, Cancel/Apply/Undo and persistence. Existing MP4 export coverage now includes cue-driven typing with real mixed audio and snowfall.

This feature does not add photo lip-sync, online video generation, speech cloning or automatic image understanding. Existing duration-fit, background weather, templates, editable projects and poster export remain available.
