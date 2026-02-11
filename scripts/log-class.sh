#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${1:-}"
LOGGER_NAME="${2:-}"
LEVEL="${3:-}"

if [[ -z "${CONFIG_FILE}" || -z "${LOGGER_NAME}" || -z "${LEVEL}" ]]; then
  echo "Usage: $0 <config-file> <logger-name> <LEVEL|OFF>"
  echo "Example: $0 ./config/logging.properties com.fileexplorer.ui.MainController FINER"
  exit 2
fi

if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "Config file not found: ${CONFIG_FILE}"
  exit 2
fi

# Normalize (trim spaces around '=' is acceptable in JUL, but keep it consistent)
LINE="${LOGGER_NAME}.level= ${LEVEL}"

# If entry exists, replace; else append.
if grep -qE "^\s*${LOGGER_NAME//./\.}\.level\s*=" "${CONFIG_FILE}"; then
  # shellcheck disable=SC2001
  sed -i -E "s/^\s*${LOGGER_NAME//./\.}\.level\s*=.*/${LINE}/" "${CONFIG_FILE}"
else
  echo "" >> "${CONFIG_FILE}"
  echo "# Added by scripts/log-class.sh on $(date -Iseconds)" >> "${CONFIG_FILE}"
  echo "${LINE}" >> "${CONFIG_FILE}"
fi

echo "Set ${LOGGER_NAME}.level = ${LEVEL} in ${CONFIG_FILE}"
