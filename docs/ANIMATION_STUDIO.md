# Animated designs (1.2.0)

Open **Animate / Video** in the editor's top toolbar, or **Export → Animated MP4 /
GIF**. Poster editing and PNG/JPG exports continue to use the complete design.

## Animation and timeline

Select a layer, choose Typing, Words, Fade, Slide, Zoom, Highlight or None, then
set its appear/disappear times and reveal duration. Typing reveals complete
Unicode clusters so Urdu marks, surrogate pairs and joined emoji are not split.
The currently revealed prefix is shaped again by Android's Urdu text renderer.
Text boxes retain their full-layout top position while revealing. The optional
cursor blinks during typing. Repeat adds a one-second hold between cycles.

Highlight advances across words using the configured timing. Voice alignment is
manual: use the audio start offset and layer timings. This is not speech
recognition or automatic word alignment. Handwriting stroke simulation is not
included. Optional **Urdu text → voice** synthesizes the selected text (or all
visible text if no text layer is selected) using the phone's TTS engine. An Urdu
voice must be installed/supported; network requirements vary by engine. The app
does not substitute an English voice or require a paid API key. Generation is
cancellable, and its audio is saved as the voice track; timings remain manual.

Scene duration is 1–60 seconds. Layer timeline lists all layer/track timings.
Backgrounds support slow zoom/pan and optional rain/particles. Preview has Play,
Pause, scrubbing and optional looping with both audio tracks. The deterministic
animation renderer is shared by preview, GIF and MP4. Selection handles and
editor guides are excluded from exported frames.

Video templates create a new, separate editable design (poetry reel, quote,
birthday greeting or invitation); the existing draft is saved first.

## Voice and music

Use **Voice / music** to record a voice track (up to 60 seconds), import voice or
import music. Microphone permission is requested only after choosing Record.
Stop & use finalizes the recording. Cancel, leaving the app or rotating the
screen cancels an unfinished recording and releases the microphone.

One voice and one music track can be mixed. Imports are copied into private
storage (100 MB limit), so picker grants need not survive an app restart.
Each track supports source trim, video start offset, volume, fade in and fade
out. Lower volumes if combining loud tracks causes distortion. Only the part
inside the scene is encoded. Previously imported assets remain for Undo/Redo.

The decoder accepts device-supported audio containers/codecs producing 16-bit
or float PCM; supported formats can vary by Android device. Unsupported/corrupt
sources report an export error rather than silently dropping the selected audio.
The export mixer converts PCM to 44.1 kHz stereo, applies both track envelopes,
limits the sum, encodes AAC and muxes it with H.264 video.

## Export

- MP4: 720p or 1080p, 24 fps, H.264 + AAC when audio is present. Canvas aspect
  ratio is preserved, with even dimensions and a 1280/1920-pixel long-side cap.
  Export runs off the UI thread, shows progress and supports cancellation.
- GIF: RGB332 palette (256 colors), up to 360 pixels wide / 640 high, 8 fps,
  maximum 10 seconds, looping and no audio. MP4 is better for long scenes,
  gradients, higher resolution or voice/music.
- Save writes a local copy; Android 10+ also attempts MediaStore insertion into
  Movies/UrduTyper or Pictures/UrduTyper. **Saved animations** can open/share local
  copies even on older Android or if gallery insertion fails. These media files
  are separate from the Cloud poster backup. Draft settings and audio paths
  persist locally in backward-compatible schema-v2 documents.

Keep the app open during export. Cancellation/errors remove incomplete outputs;
video/audio intermediate files are removed in all outcomes. Existing finished
exports and the editable draft remain intact.

## Validation

Do not build or run tests in the cloud checkout. Push one complete batch and use
one GitHub Actions run. The workflow runs JUnit/Robolectric tests, builds the APK,
then uses an Android 29 emulator to exercise the actual H.264/EGL/AAC pipeline.
The smoke test encodes typing with two generated audio tracks, verifies MP4 video
and AAC tracks, decodes changing video frames, and checks audible decoded PCM.
Unit tests cover Urdu/emoji boundaries, timings, repeat, gains/fades, old draft
defaults, deterministic frames, GIF first-frame decoding and cancellation. Real
multi-frame GIF decoding, playback duration and changing frames are verified on
the emulator because Robolectric does not provide the deprecated Movie decoder.

Before release, also check a physical phone: record/import actual voice/music,
align timings, preview and scrub, reopen the draft, share a video to WhatsApp,
inspect GIF playback, export 720p/1080p and cancel a long export. Test microphone
denial, app backgrounding during recording, and a full/low-storage device.


## Storyboard editor update

The editor now has an inline play/pause control and scrubber. Return to Edit to
manipulate layers. The Video tab shows a scene strip and a scrollable visual
timeline. Drag a layer/track body to move it, or an edge to change its timing;
tap a clip for exact settings. A timeline gesture records one undo step.
Waveforms are decoded locally from imported audio, not decorative placeholders.

Scenes supports add, duplicate, delete, reorder, and None/Fade/Slide/Dissolve
entrances with adjustable 0.1–2 second duration. The maximum is 20 scenes and
60 seconds for the entire video. Scene transitions occupy the beginning of the
incoming scene and do not overlap or shorten the overall video. Poster exports
use the scene being edited; MP4/GIF and preview use the ordered storyboard.
Global voice/music tracks span the storyboard. Music can automatically lower
while audible voice plays. Audio fades, volume and trimming remain available.

Project → Batch poetry makes a separate saved design from blank-line-separated
verses (up to 12, 5 seconds each). Brand kit stores a text style and optional
image-layer logo locally. Safe margins are editor-only. Fonts, effects, style
sliders and built-in backgrounds preview on the canvas with Apply/Cancel.
An image layer can fill a crop frame without stretching its original proportions.
Home has direct New design/New video/Templates actions. Hold a template to add
or remove it from Favorites; the Favorites chip filters the grid.

Video → Import SRT captions accepts reviewed subtitle timing within 60 seconds,
including Urdu, and adds editable highlighted text layers across scene cuts.
Video → Beat timing places scene cuts on a user-entered BPM/beat grid aligned
with the music start offset. These are file import and manual timing tools,
not automatic speech recognition or automatic beat detection. AI background
generation and automatic transcription still require a configured provider.

Export shows complete duration, scene count, selected MP4 resolution/audio and
an approximate file-size estimate. Completed exports offer Play/Share; errors
offer a quality-change/retry path. Keep the app open during export.

Additional regression coverage checks old schema-v2 drafts, per-scene save and
undo, storyboard resize, caption splitting at cuts, ducking/muting, timeline
gesture/cancel boundaries, blended scene frames and actual multi-scene MP4
encoding on Android. Physical phone layout and imported media still need device QA.

## Voice timing and background weather

Video → Audio or Animate → Match text & voice offers Fit selected text and Fit whole video. The selected text fits the trimmed voice overlap in its scene. Whole-video fit paces scenes by editable text word counts. The voice offset is preserved, with up to 0.3 seconds final hold. Text remains visible through that hold. Words/Typing/Highlight are retained; other effects become Words, with repetition disabled. Locked or hidden text is not fitted. Inadequate voice length or a duration over the 60-second budget reports an error instead of silently truncating audio. Apply/Cancel and Undo remain available.

This is duration fitting, not speech recognition or word-level transcription. Imported and recorded voice retains up to 60 seconds even when the movie is shorter, allowing Fit to extend the design. Music import behavior is unchanged.

Video → Weather offers Auto, None, Autumn leaves, Rain, Snowfall and Particles, with intensity and speed. Deterministic effects are shared by preview, MP4 and GIF. Auto uses background filename hints and conservative color-palette heuristics, not AI object recognition. Ambiguous pictures get no effect. Set the photo as the scene background for suggestions. Original image names persist locally per scene. New photo backgrounds enable Auto if the previous mode was None or Auto; manual choices stay manual.

No online generation, external AI API or lip-sync is introduced. Unit tests verify voice offsets/trims, scene allocation, final text hold, invalid duration rejection, weather defaults/persistence, deterministic frames and Auto/manual equivalence. Native checks exercise Fit/Cancel/Apply/Undo, Auto leaves, manual snow, draft persistence and MP4 encoding with snowfall.
