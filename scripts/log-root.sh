#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${1:-}"
LEVEL="${2:-}"

if [[ -z "${CONFIG_FILE}" || -z "${LEVEL}" ]]; then
  echo "Usage: $0 <config-file> <LEVEL|OFF>"
  echo "Example: $0 ./config/logging.properties INFO"
  exit 2
fi

if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "Config file not found: ${CONFIG_FILE}"
  exit 2
fi

LINE=".level= ${LEVEL}"

if grep -qE "^\s*\.level\s*=" "${CONFIG_FILE}"; then
  sed -i -E "s/^\s*\.level\s*=.*/${LINE}/" "${CONFIG_FILE}"
else
  echo "${LINE}" >> "${CONFIG_FILE}"
fi

echo "Set root .level = ${LEVEL} in ${CONFIG_FILE}"
