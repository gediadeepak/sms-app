# Sandesh logo and appearance

The user supplied the original winged envelope, supporting hand and Sandesh wordmark. The app retains both the winged envelope and supporting hand. Wordmark typography is rendered natively for sharp text.

The final transparent asset is `app/src/main/res/drawable-nodpi/sandesh_logo.png`. It was adapted using the built-in image-generation tool, not the API/CLI fallback. The first envelope-only draft was discarded after the user clarified that the hand must stay. The final PNG has genuine alpha transparency; it is not a black-background image.

Final asset prompt:

> Preserve all three core motifs from the original: the rounded chat-envelope with V flap and lower-left tail, the upward three-feather wings, and the open supporting hand and wrist below, palm up, fingers pointing to the upper right. Keep the hand’s lifting gesture. Remove the black background, glow, blur, shadows and separate lettering. Adapt to a flat royal-blue #2856D8 single-colour mark with transparent negative spaces inside the envelope and between fingers, on a genuinely transparent square canvas with padding. No crop, no extra elements, no omitted hand.

Native Compose tints that alpha asset blue, forest green or white. The logo therefore follows all three app themes without generating or storing separate recoloured bitmaps. It is used on setup, in the sidebar, the flying splash and the adaptive launcher icon. The black/white theme also uses grayscale avatars and status text.

The splash plays the user's supplied 27-frame, 1.8-second GIF from `app/src/main/res/raw/sandesh_flight.gif` once per fresh activity launch. It preserves the hand, flying envelope, native wordmark and original timing. A native playback colour matrix turns the cream background into transparency and applies the selected blue, green or white accent. The GIF file itself is unchanged. Playback respects Android's animation duration scale and can be skipped with Back. Android may display its standard launcher-icon splash briefly before the Compose splash.

The original upload is kept locally in ignored `.codex-attachments/`; only the selected production asset is committed. No unapproved source upload or discarded draft is included in the repository.
