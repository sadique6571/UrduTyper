# Midnight Luxe

Approved direction: deep navy surfaces, warm gold actions, ivory text, thin slate borders.
Home shows two creation cards, six native template previews and recent editable projects.
The editor uses one contextual icon rail; More retains geometry, grid, pan, caption, beat,
brand and project controls. Video mode adds playback, scene thumbnails and a gold/slate
visual timeline. Poster mode leaves this space to the canvas.

Six original image backgrounds are bundled as optimized WebP assets: moonlit lake,
sunset peak, ivory blossom, lantern night, mosque sunset, misty mountains. Their poetry
is an independent editable layer. Background keys persist in drafts and are shared by
the Home gallery, Templates, background picker and both poster/video rendering paths.
These are newly generated backgrounds following the chosen concept, not crops of its UI.

CI captures Home, Poster and Video from the native Android app and uploads
Midnight-Luxe-actual-screenshots alongside the APK. Device QA remains necessary for
physical touch, font rendering, small screens and landscape.

## Bottom controls and editing update

Undo, Redo, Canvas, Preview, Poster and Video are always available in a fixed
bottom quick-control strip. Poster/Video are explicit selected modes; switching
does not create another draft or change layers. The former second top toolbar
is removed. Canvas presets show their proportions and current selection; resizing
is one undo step and scales all scenes. Exact custom dimensions remain validated.

Tap a text layer for Edit, Fonts, Size, Color, Layers and More; double-tap still
opens the text editor immediately. Size opens the live font-size control. More
retains effects, adding layers, geometry, zoom/pan, recording and project tools.

Preview opens a full-screen, read-only renderer with no selection handles or
guides; video preview includes playback, audio and seeking. Preview is also in
both export screens. Poster export summarizes output dimensions, format,
transparency and quality. Layers now include thumbnails beside select/lock/hide/
reorder controls. Timeline grips are visibly wider, choose the nearest edge for
short clips, and track labels open exact timing controls.

The native interaction check resizes a poster, tests real Undo/Redo, switches modes
without changing the document ID, verifies bottom-control placement, opens and
closes the guide-free preview, and captures the resulting poster/video screens.

## Font, style and video inspectors

Fonts now use searchable cards showing the selected text in each actual font,
with All/Recent/Favorites/Imported filters, persisted favorites, ordered recent
choices and TTF/OTF import. Latin-only fonts are clearly marked and cannot replace
Urdu text. Preview changes are discarded on Cancel and recorded as one edit on Apply.

Text styling uses Style/Color/Effects tabs, live formatting and spacing controls,
solid/custom-gradient palettes, recent colors, visual Classic/Gold/Neon/Emboss
presets, an optional full effect library, and real effect-strength/shadow controls.
New optional layer fields keep old schema-v2 drafts compatible. The same renderer
is used by the inspector artwork, live canvas, poster, GIF and MP4.

Video uses Scene/Animate/Audio/Export tabs. Scene duration honors the remaining
60-second movie budget and adjusts all layer timings. Animated layers reserve a
short final hold so fully revealed Urdu text is visible before the layer ends.
The same timing rules apply to timeline trims, duplicated scenes and beat timing.
Audio has waveform-backed trim controls, volume, start offsets, fades and music
ducking. Scene/animation validation does not block Audio or Export navigation.
Preview does not commit changes. Apply records staged changes; Export explicitly
applies its staged settings and exports. GIF preview has no audio, and videos over
10 seconds cannot export as GIF. Starting a new video template aborts if the
current draft cannot be saved.

Inspectors keep artwork, tabs and a pinned Apply/Cancel footer. Compact/Expand
adjusts panel height. CI checks font and style Cancel/Apply/Undo behavior, temporary
video edits, independent tabs, silent GIF preview rules, persisted style fields,
font preferences, scene-duration budgets and complete final typing frames.
Additional device screenshots are 04-font-library, 05-text-styling, 06-video-studio.
