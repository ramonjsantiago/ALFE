# HOTFIX195 / Phase 4P.9CW — Command Bar Template Fidelity, Segment Spacing, and Right-Rail Action Parity

This hotfix advances the top command bar toward Windows File Explorer visual parity by normalizing the command template used across the row and tightening the right-side action rail.

Included:
- converted `Cut`, `Copy`, `Paste`, `Rename`, `Share`, and `Delete` from icon-only presentation to icon+text command-bar entries
- kept `New`, `Sort`, and `View` on the same structured icon/text/chevron template with tighter Windows-style spacing
- introduced a grouped right rail for `Preview`, `Operations`, and `Details` with Explorer-like divider treatment
- tightened command-bar row height, label/icon spacing, separator padding, and chevron spacing for a closer Explorer silhouette

Primary files changed:
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `src/main/resources/com/fileexplorer/ui/css/address-command-parity.css`


## H195 corrective pass — structured menu vertical alignment

Adjusted the structured command-bar MenuButton content baseline so **New**, **Sort**, and **View** align vertically with adjacent command-bar buttons such as **Delete**, **Share**, and the neighboring actions.


## H195 final corrective pass — structured menu vertical alignment

Applied a stronger structured-menu baseline lift so **New**, **Sort**, and **View** no longer render visually lower than adjacent command-bar entries in the top toolbar. This pass raises the structured graphic container, icon slot, label, and chevron together for closer Windows Explorer parity.

- Corrective pass: raised structured New / Sort / View command-bar menu content an additional 2 px for closer vertical baseline parity.

- HOTFIX195 follow-up: pinned dark top-chrome / command-bar labels, glyph icons, vector icons, and chevrons to white for stronger Windows Explorer parity.

- HOTFIX195 crash follow-up: guarded late metadata/search/background dispatch during controller shutdown so debounced FX callbacks no longer submit work into a terminated IO executor.


## H195 follow-up — BIN thumbnail icon resource integration

Added the supplied BIN artwork as dedicated `.bin` file icon resources under `src/main/resources/com/fileexplorer/ui/icons/light` and `src/main/resources/com/fileexplorer/ui/icons/dark`, and routed `ext:bin` through `IconLoader` so BIN items now render with the requested file-thumbnail/icon asset.

Also retained the recent responsive virtual-icon crash correction by guarding `prefWidth` writes when the virtual icon list/grid controls are already width-bound.


## H195 follow-up — ISO thumbnail icon resource integration

Added the supplied disc artwork as dedicated `.iso` file icon resources under `src/main/resources/com/fileexplorer/ui/icons/light` and `src/main/resources/com/fileexplorer/ui/icons/dark`, and routed `ext:iso` through `IconLoader` so ISO items now render with the requested file-thumbnail/icon asset.


## H195 INI thumbnail icon update
- Added the supplied .ini icon artwork under `src/main/resources/com/fileexplorer/ui/icons/light/` and `.../dark/` as `ini-16/24/32/48/64/96/128/256.png`.
- Existing `IconLoader` ext:ini override continues to route `.ini` files to the dedicated resource set.

## Incremental asset updates
- Added dedicated `.reg` thumbnail/icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` with generated sizes `16, 24, 32, 48, 64, 96, 128, 256`.
- Routed `.reg` extension lookup through `IconLoader` identity override resources.

## Home icon update
- Added dedicated home icon resources under `src/main/resources/com/fileexplorer/ui/icons/light/` and `src/main/resources/com/fileexplorer/ui/icons/dark/` as `home-16.png` through `home-256.png`.
- Wired the Home tab and Home pinned/recent buttons to use the dedicated Home icon resource.

## H195 asset update — Undo icon resource
- Replaced the Undo menu icon resource with the supplied artwork under `src/main/resources/icons/see_more_undo.png`.
- Mirrored the same asset to `src/main/resources/icons/see.more.undo.png` to keep both legacy resource names aligned.



## H195 asset update — Disk icon resource
- Added dedicated disk icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` as `disk-16.png` through `disk-256.png`.
- Routed filesystem root drives through `special:disk` so drive-root thumbnails/icons use the supplied disk artwork in file surfaces, home buttons, and icon-based navigation cells.


## H195 asset update — Network drive icon resource
- Added dedicated network-drive icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` as `network-drive-16.png` through `network-drive-256.png`.
- Routed UNC / network-drive roots through `special:networkdrive` so network drive thumbnails/icons use the supplied artwork.


## H195 asset update — Local disk icon resource
- Added dedicated local-disk icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` as `local-disk-16.png` through `local-disk-256.png`.
- Routed non-network root drives through `special:localdisk` so local disk thumbnails/icons use the supplied artwork while preserving the separate network-drive icon route.


## H195 asset update — Video file placeholder thumbnail icon
- Added dedicated video file placeholder icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` as `video-16.png` through `video-256.png`.
- These resources are used as the generic video file icon/placeholder until an actual video thumbnail image is resolved.

- Updated generic video placeholder icon resources to the latest user-supplied artwork under `src/main/resources/com/fileexplorer/ui/icons/light|dark/video-*.png`.

- Added dedicated `.ico` placeholder icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` so icon files show the supplied artwork until thumbnail decode resolves.


## This PC icon resource update
- Added dedicated `special:thispc` icon routing.
- Added `this-pc-16/24/32/48/64/96/128/256.png` under both light and dark icon resource folders.
- Wired the navigation tree root to use the new This PC artwork.
