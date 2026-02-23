#!/usr/bin/env bash
set -euo pipefail

# Creates a lightweight distribution zip suitable for attaching to a GitHub Release.
# Output: dist/FileExplorer-<version>-<shortSha>.zip

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION="$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec 2>/dev/null || true)"
if [[ -z "$VERSION" ]]; then
  # fallback if exec plugin isn't present
  VERSION="$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')"
fi

SHA="${GITHUB_SHA:-}"
if [[ -z "$SHA" ]]; then
  SHA="$(git rev-parse --short HEAD 2>/dev/null || echo local)"
else
  SHA="${SHA:0:7}"
fi

DIST_DIR="$ROOT_DIR/dist"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

JAR="target/FileExplorer-$VERSION.jar"
if [[ ! -f "$JAR" ]]; then
  # fallback to any jar that matches
  JAR_FALLBACK="$(ls -1 target/*.jar 2>/dev/null | head -n 1 || true)"
  if [[ -z "$JAR_FALLBACK" ]]; then
    echo "No jar found in target/. Did 'mvn package' succeed?" >&2
    exit 1
  fi
  JAR="$JAR_FALLBACK"
fi

BUNDLE_ROOT="$DIST_DIR/FileExplorer-$VERSION"
mkdir -p "$BUNDLE_ROOT"

cp -a "$JAR" "$BUNDLE_ROOT/"
cp -a README.md README.txt CHANGELOG.md "$BUNDLE_ROOT/" 2>/dev/null || true
cp -a scripts "$BUNDLE_ROOT/scripts" 2>/dev/null || true

# Provide simple run scripts
cat > "$BUNDLE_ROOT/run.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/$(basename "$JAR")" "$@"
EOF
chmod +x "$BUNDLE_ROOT/run.sh"

cat > "$BUNDLE_ROOT/run.ps1" <<'EOF'
Param(
  [Parameter(ValueFromRemainingArguments=$true)]
  [string[]]$Args
)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Get-ChildItem -Path $dir -Filter "*.jar" | Select-Object -First 1
if (-not $jar) { throw "No jar found in bundle folder." }
java -jar $jar.FullName @Args
EOF

ZIP_NAME="FileExplorer-${VERSION}-${SHA}.zip"
( cd "$DIST_DIR" && zip -r "$ZIP_NAME" "FileExplorer-$VERSION" >/dev/null )

echo "Created: $DIST_DIR/$ZIP_NAME"
