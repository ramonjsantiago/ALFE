# H187 Text File Icon Update

This update adds the attached icon as the default packaged icon for `.txt` and `.text` files while text content/loading is pending.

## Updated
- `src/main/java/com/fileexplorer/util/IconLoader.java`
- `src/main/resources/com/fileexplorer/ui/icons/light/txt-*.png`
- `src/main/resources/com/fileexplorer/ui/icons/dark/txt-*.png`
- mirrored copies under `target/classes/com/fileexplorer/ui/icons/`

## Behavior
- `ext:txt` resolves to the dedicated text-file resource icon
- `ext:text` resolves to the dedicated text-file resource icon
- both extensions use the attached image across supported icon sizes
