# HOTFIX208 / Phase 4P.9DJ

## Archive Icon Identity Routing and Compressed Resource Parity

This hotfix fixes compressed/archive placeholder icon routing on the H204 compile-fix baseline.

### What changed
- Corrected `IconLoader.resourceNameFor(...)` so `IconType.ARCHIVE` resolves to the packaged `compressed-*.png` resources instead of the non-existent `archive-*.png` path.
- Expanded `IconLoader.loadIdentityOverride(...)` to recognize `kind:archive` and the compressed-file extension identities `zip`, `7z`, `rar`, `tar`, `gz`, `bz2`, `xz`, and `zst`.
- Preserved the existing packaged compressed icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/compressed-*.png`; no image asset changes were required.

### Expected result
- ZIP and other compressed/archive file types now show the packaged compressed placeholder icon instead of falling back to the generic file image.
- Archive placeholders stay consistent across Details, icon surfaces, preview fallbacks, and any other path that resolves through the shared icon identity model.
