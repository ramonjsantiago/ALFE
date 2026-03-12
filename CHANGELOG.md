## HOTFIX35 / Phase 4K.3 — Icon / Tile Selection Parity
- Added mode-aware icon/tile metrics so Extra Large, Large, Medium, Small, Tiles, and Content each get distinct widths, spacing, and padding.
- Added explicit icon-flow mode classes plus icon-tile mode classes so icon/tile geometry can be tuned without changing Details view behavior.
- Refined grid icon tiles with narrower label measure, steadier vertical rhythm, and Windows 11 style rounded selection bounds.
- Refined Tiles and Content rows with Explorer-like name/meta typography and wider left-aligned content blocks.

## HOTFIX31 / Phase 4M — Startup Pipeline Triage and Deferred Work Budget
- Added a bounded startup scheduler with a dedicated critical queue plus a maximum idle deferral timer, so deferred work no longer drifts for many seconds just because the pointer stays active.
- Moved critical stylesheet attachment onto the new bounded queue and kept non-critical stylesheet layers on the idle queue with a small batch/time budget.
- Moved startup self-check, queue restore, and orphan-temp scanning off the FX thread and back onto the UI thread only for dialogs and final guard release.
- Promoted initial-folder open to the critical queue so the shell-to-content handoff is more predictable after the main UI is installed.
- Split MainController.setScene deferred work into separate theme, font, and motion passes to reduce CSS/layout contention in the first usable frames.


## HOTFIX30 / Phase 4K.2A — Details Hover Root-Cause Elimination
- Moved selection-state-tokens.css into the critical stylesheet attach path so Details hover and selection colors exist before first interaction.
- Removed the later idle re-attach of selection-state-tokens.css to avoid a second row-style pass after the table is already live.
- Localized Details row hover/selection looked-up colors inside details-view-parity.css so Details rows no longer depend on a later stylesheet for paint conversion.
- Explicitly neutralized generic TableView row hover/selected and child table-cell background rules inside explorer-details-table to keep Details highlight ownership single-sourced.


## HOTFIX29 / Phase 4K.2 — Details Row Renderer Parity
- Reworked the Details/Table row renderer to use explicit stable row-state classes for even/odd, hover, active selection, and inactive selection.
- Removed remaining Details-row hover styling conflicts from selection-state-tokens.css so row visuals are owned by details-view-parity.css.
- Switched row hover synchronization from mouse entered/exited handlers to row hoverProperty listeners, which eliminates the repeated highlight flapping seen while moving across row cells.
- Rounded and inset the Details row highlight to read closer to Windows 11 File Explorer instead of a flat JavaFX stripe.
- Disabled the generic focused-selected row inset override from context-menu-parity.css so Details rows no longer fight two competing selection shapes.


## HOTFIX28 / Phase 4K.1B — Table Hover Stability + CSS Parse Fix
- Fixed JavaFX CSS parser warning caused by unsupported scientific notation in context-menu-parity.css.
- Stabilized Details/Table hover highlight with explicit row hover class instead of relying on pseudo-class hover paint.
- Removed unnecessary hover listeners from row-bound table cells to reduce CSS churn during pointer movement.

## HOTFIX27 / Phase 4K.1A — Hover Highlight Stabilization
- stabilized icon/tile hover highlighting by switching from CSS `:hover` to an explicit `explorer-hover` class
- made tile hit-testing container-driven so labels/icons no longer flap hover state while the pointer moves inside a tile
- preserved selected + hover visual strength using the same selection-state token layer

- HOTFIX26 / PHASE4K_1: introduced centralized selection-state design tokens and hooked them into details, navigation, and icon/tile selection scaffolding.
- Added selection-state-tokens.css as a final Explorer styling layer for active, inactive, hover, border, and focus-ring states.
- Details rows and navigation tree rows now consume shared selection tokens instead of repeating literal state colors.
- Icon/tile containers now expose explorer-icon-tile hooks and refresh visible selected-state classes from the table selection model.

- HOTFIX25 / PHASE4J_1: added keyboard/focus/selection polish for the details table.
- F2 now targets the focused row for inline rename and collapses multi-selection to the active row before edit.
- Enter now opens the focused item, including files, instead of only entering folders.
- Shift+F10 and the Context Menu key now open the file-operations menu anchored to the focused row.
- Escape now closes transient explorer menus and cancels inline rename while restoring focus to the edited row.
- Right-click on an already-selected row now preserves multi-selection; right-click on an unselected row first focuses/selects that row.
- Inline rename now restores focus after cancel/no-op and preserves the renamed target across refresh so selection lands on the new name after refresh completes.

- HOTFIX8: flattened the table header, reduced header/filler border contrast, and softened header hover fill so the details header reads closer to Windows 11 File Explorer.
# Change Log

- HOTFIX7: Made the tree disclosure chevrons slightly lighter and thinner to better match Windows 11 File Explorer.
- Reduced chevron stroke width from 1.5 to 1.2.
- Reduced the disclosure hitbox from 18px to 16px so the chevron reads less heavy.
- Tightened disclosure padding and slightly shrank the chevron path geometry for a subtler Fluent-style look.

- HOTFIX9: stronger Windows 11 context menu treatment with rounder corners, softer shadow, and more even left gutters.

- HOTFIX10: tuned nav, breadcrumb, and table-header spacing to a steadier 8px rhythm with aligned left/right gutters and cleaner top-surface spacing.

- HOTFIX11: refined typography toward Windows 11 / Segoe UI metrics by moving key surfaces to Segoe UI Variable Text, normalizing menu / tree / breadcrumb text to 13px, and calming table-header labels to 12px with lighter weight.

## PHASE4A_3 HOTFIX12 — tree chevron alignment, View→Show submenu arrow, Fluent micro-motion
- nudged navigation-tree disclosure chevrons down slightly so they line up better with Segoe UI text metrics
- changed the View > Show row to use a real submenu arrow again instead of rendering as an ellipsis row
- added subtle Fluent-style hover / press micro-motion for toolbar, nav, and breadcrumb buttons
- softened hover / armed fills so the micro-motion reads calmer and less like default JavaFX
- HOTFIX13: lowered the tree disclosure chevron artwork inside its fixed disclosure box so the chevron center tracks the row text block more closely.

- HOTFIX14: hard-centered tree row content by aligning disclosure node, icon, and text block to the row visual center in both tree cell implementations.

- HOTFIX15: expanded the Choose Details dialog catalog with the requested Explorer-style field list, lazily creates selected detail columns on demand, removes unchecked detail columns from the active details table, and preserves order/visibility/width state across sessions.
- Added a resource-backed Choose Details catalog so the app does not eagerly instantiate hundreds of optional columns.
- Added a lazy detail-column factory with basic values for path/name/type/size/date-created/date-accessed/owner/attributes and blank placeholders for unsupported metadata fields.
- Updated the header details menu installer to rebuild from the current detail-column set so new lazily-created columns can participate without a restart.

- HOTFIX16: forced initial details-table Type, Size, and Date modified cell text to use the same primary text color model as Name, with selected rows staying white, so those columns no longer render darker than Name during startup theme/style transitions.

## HOTFIX32 / Phase 4M.1 — Reentrant Startup Suppression and Single-Pass Bootstrap
- added a stage-scoped bootstrap state machine and suppression guards to keep normal launch on a single pass
- logged bootstrap reason, target folder, and external caller summary for configureExplorerStage requests
- required explicit secondary-window reasons for non-primary bootstrap paths
- suppressed duplicate initial-folder bootstrap opens on the same Stage
- removed redundant stage.show() from MainController new-window creation
- updated breadcrumb/new-window call sites to use explicit secondary bootstrap reasons

## HOTFIX33 / Phase 4K.2B — Details Hover Ownership Lockdown
- removed Details-row hover / selected style-class churn so TableRow state is driven by built-in pseudo-classes instead of repeated class mutation
- made Details table cells mouse-transparent by default so row hover ownership stays stable while moving across columns
- preserved inline rename interaction by only restoring mouse input while the rename field is active
- rewrote details-view-parity.css row painting to use :filled / :hover / :selected / :odd / :empty selectors with stronger Details-specific overrides
- kept generic table hover / selection rules from bleeding back into Details view row rendering
## HOTFIX34 / Phase 4K.2C — Details Hover Hit-Test Isolation + Choose Details Dialog Parity
- stabilized Details row hover using a single row-owned custom pseudo class
- neutralized native row :hover paint in Details view to avoid flicker across subcells
- added Choose Details dialog dark-mode parity, checkbox-first rows, Show/Hide buttons, and width editing



## HOTFIX36 / Phase 4K.3A - Size Column Alignment and Flow Layout Expansion
- right-justified the Details/Table Size column
- moved List view onto the flow-based icon renderer
- switched List/Tiles flow wrapping to viewport-driven flow metrics
- refreshed icon flow wrap length when the ScrollPane viewport changes


## HOTFIX37 / Phase 4K.2D — Details Hover Stabilization + Correct Per-Item Tooltips
- moved Details hover ownership from table-level hit-test tracking to per-row hover state
- deferred metadata-driven TableView refreshes while a Details row is hovered, then flushed them once hover exits
- added per-row Details tooltips that rebuild from the currently hovered row item when shown
- added per-item icon/list/tile/content tooltips that rebuild from the hovered path when shown


## HOTFIX39 / Phase 4K.2F — Details Overlay Hover Renderer and Delayed Metadata Popup
- Reworked Details hover resolution to use table-level row geometry and explicit row presentation updates.
- Keyed Details metadata popup display to the hovered visible row index with delayed popup refresh.
- Neutralized CSS-owned Details row hover paint so inline row presentation is the single owner of hover/selection visuals.
