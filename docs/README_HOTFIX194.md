# HOTFIX194 / Phase 4P.9CV — Explorer Top-Chrome Metrics, Icon Alignment, and Command-Bar Padding Parity

This hotfix advances top-chrome visual parity against the supplied Windows Explorer reference.

Included:
- tighter tab strip metrics with refined active-tab / plus-button / inline-close geometry
- reduced address-row height and matched address/search shell heights
- normalized nav button hit targets and icon alignment
- command-bar padding and separator metrics pass for icon-only and icon+text controls
- structured Filter command added to the command bar to match the reference silhouette

Primary files changed:
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `src/main/resources/com/fileexplorer/ui/breadcrumb/BreadcrumbBar.fxml`
- `src/main/resources/com/fileexplorer/ui/css/address-command-parity.css`
- `src/main/resources/com/fileexplorer/ui/css/home-tabs-parity.css`
- `src/main/java/com/fileexplorer/controller/MainController.java`


Follow-up correction:
- removed the native `MenuButton` arrow lane from the structured Sort/View/Filter presentation so their custom icon/text/chevron content centers on the same vertical rhythm as Delete, Share, and the other command-bar controls
- nudged structured menu icon/content baselines upward slightly to match the surrounding command-bar glyph row more closely


Follow-up correction 2:
- wired the bottom status-bar `Details` button to switch the file surface into Details view
- wired the bottom status-bar `Large` button to switch the file surface into Extra large icons view
- assigned the supplied icon assets to the two status-bar view buttons and kept their selection state synchronized with the current view mode


Follow-up correction 3:
- removed the text labels from the bottom status-bar view buttons so the Details and Extra large icons toggles render as icon-only buttons while preserving their existing view-switch behavior


Follow-up correction 4:
- removed the top command-bar `Filter` menu
- replaced the top `Preview` button icon with the newly supplied asset
