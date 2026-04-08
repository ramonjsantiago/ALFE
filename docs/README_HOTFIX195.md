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
