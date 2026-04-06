# HOTFIX178 / Phase 4P.9CF — AVIF/WebP Thumbnail Decode Support and JUL Logging Unification

## Scope
This phase adds thumbnail pipeline support for WebP plus HEIF/AVIF-family formats and finishes the remaining runtime logging cleanup so the project routes logs through `java.util.logging`.

## What changed
- Added `com.twelvemonkeys.imageio:imageio-webp:3.12.0`
- Added `com.github.gotson.nightmonkeys:imageio-heif:${nightmonkeys.version}` at runtime scope
- Replaced the runtime SLF4J binder from `slf4j-nop` to `slf4j-jdk14`
- Added `--enable-native-access=ALL-UNNAMED` to the JavaFX Maven launch options
- Added ImageIO capability probing / gating for `.webp`, `.avif`, `.heif`, and `.heic`
- Expanded image/icon classification for AVIF/HEIF-family extensions
- Replaced remaining direct console / stack-trace runtime logging in the targeted classes with JUL

## Native runtime note for AVIF / HEIF
NightMonkeys still needs the platform HEIF/AVIF native libraries to be available via `java.library.path` (or the platform library search path). If those native libraries are absent, the app now degrades cleanly to the normal file-type icon and logs a one-shot JUL warning instead of repeatedly retrying failed thumbnail decodes.

## Files changed
See `CHANGED_FILES.txt`.

## Verification checklist
1. Refresh Maven dependencies and rebuild the project.
2. Run `mvn javafx:run` and confirm no SLF4J NOP binder warnings appear.
3. Open folders containing `.webp` files and confirm thumbnails render through ImageIO.
4. Open folders containing `.avif`, `.heif`, or `.heic` files and confirm thumbnails render when the native HEIF/AVIF libraries are available.
5. Temporarily remove HEIF/AVIF native libraries and confirm the app falls back to file-type icons with one JUL warning per extension instead of repeated stack traces.
