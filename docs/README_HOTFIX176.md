# HOTFIX176 / Phase 4P.9CD — Realization Priority Bands, Frame-Budgeted Decode Promotion, and Scroll-Stop Commit Parity

This project is the **fully populated HOTFIX176 integration** built on the attached **H175** baseline.

## Integrated changes

### MainController wiring
- Added HOTFIX176 viewport scheduling primitives under `com.fileexplorer.perf.viewport`
- Reworked viewport realization refresh to build **visible / near-viewport / far-offscreen** work bands
- Routed refresh execution through `BudgetedViewportScheduler`
- Added scheduler telemetry logging for:
  - realization runs
  - decode promotions
  - decode-promotion drops
  - frame-budget overruns
  - scroll-stop commit latency
- Changed scroll-stop behavior so the final settle pass is driven by the HOTFIX176 scheduler callback instead of a separate unconditional settle trigger

### Decode-promotion behavior
- `AsyncIconService` now reschedules **pending** work when a newer request arrives with:
  - a higher priority lane, or
  - a newer viewport scope
- `AsyncThumbnailService` now does the same for pending thumbnail decode work
- This enables **frame-budgeted promotion** from background/queued work into visible or prefetch lanes without flooding the pipeline

### New viewport scheduling package
Added:
- `RealizationPriorityBand`
- `ViewportBandClassifier`
- `FrameBudget`
- `ViewportWorkItem`
- `ViewportSchedulerTelemetry`
- `ScrollStopCommitCoordinator`
- `BudgetedViewportScheduler`
- JUnit tests for the new policy primitives

## Default tuning added for HOTFIX176
- Frame budget: `fileexplorer.realization.frameBudgetNanos` default `6_000_000`
- Scroll-stop quiet window: `fileexplorer.realization.scrollStopQuietMs` default `120`
- Near-band threshold: `fileexplorer.realization.nearThresholdCells` default `12`
- Far-work padding limit: `fileexplorer.realization.farWorkLimit` default `24`

## Source areas changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/java/com/fileexplorer/service/icon/AsyncIconService.java`
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `src/main/java/com/fileexplorer/perf/viewport/*`
- `src/test/java/com/fileexplorer/perf/viewport/*`

## Verification note
The runtime used to prepare this delivery did not include Maven or JDK 25 / JavaFX 25 for a full project build. The new generic HOTFIX176 viewport package was syntax-checked locally with `javac`, and the project was integrated source-side against the provided H175 baseline.
