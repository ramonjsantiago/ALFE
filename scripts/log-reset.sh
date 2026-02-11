#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${1:-}"

if [[ -z "${CONFIG_FILE}" ]]; then
  echo "Usage: $0 <config-file>"
  echo "Example: $0 ./config/logging.properties"
  exit 2
fi

if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "Config file not found: ${CONFIG_FILE}"
  exit 2
fi

# Remove all per-class *.level lines except core JUL ones.
# Keep:
#   .level
#   java.util.logging.ConsoleHandler.level
#   java.util.logging.ConsoleHandler.formatter
#   java.util.logging.SimpleFormatter.format
TMP="$(mktemp)"
grep -vE "^[[:space:]]*[A-Za-z0-9_$.]+\.level[[:space:]]*=" "${CONFIG_FILE}"   | cat > "${TMP}"

# Re-add the root level line if grep removed it mistakenly (should not).
mv "${TMP}" "${CONFIG_FILE}"

echo "Removed per-class logger levels from ${CONFIG_FILE}"
echo "Root .level and handler settings preserved."
