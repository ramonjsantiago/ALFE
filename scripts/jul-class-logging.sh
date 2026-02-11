#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF_FILE="$ROOT_DIR/config/logging.properties"
LIST_FILE="$ROOT_DIR/config/jul-loggers.txt"

usage() {
  cat <<USAGE
Usage:
  $(basename "$0") list
  $(basename "$0") status <logger>
  $(basename "$0") on     <logger> [level]
  $(basename "$0") off    <logger>
  $(basename "$0") all-on  [level]
  $(basename "$0") all-off

Notes:
  - <logger> should be a fully-qualified class name, e.g. com.fileexplorer.ui.MainController
  - [level] defaults to FINE (typical for method-entry tracing)
  - To run the app with this config:
      mvn -Djava.util.logging.config.file=config/logging.properties javafx:run
USAGE
}

ensure_files() {
  if [[ ! -f "$CONF_FILE" ]]; then
    mkdir -p "$(dirname "$CONF_FILE")"
    cat > "$CONF_FILE" <<'CFG'
.level=INFO
handlers=java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level=ALL
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.SimpleFormatter.format=%1$tF %1$tT.%1$tL %4$s %3$s - %5$s%6$s%n
CFG
  fi

  if [[ ! -f "$LIST_FILE" ]]; then
    echo "WARNING: $LIST_FILE not found; 'list' and 'all-*' may be incomplete." >&2
  fi
}

set_level() {
  local logger="$1"
  local level="$2"

  ensure_files

  if grep -qE "^\Q${logger}\.level\E=" "$CONF_FILE"; then
    perl -pi -e "s/^\Q${logger}.level\E=.*/${logger}.level=${level}/" "$CONF_FILE"
  else
    echo "${logger}.level=${level}" >> "$CONF_FILE"
  fi
}

do_status() {
  local logger="$1"
  ensure_files
  local line
  line=$(grep -E "^\Q${logger}\.level\E=" "$CONF_FILE" || true)
  if [[ -z "$line" ]]; then
    echo "${logger}.level is not set (inherits from parents; root is controlled by .level)"
  else
    echo "$line"
  fi
}

do_list() {
  ensure_files
  if [[ -f "$LIST_FILE" ]]; then
    cat "$LIST_FILE"
  else
    echo "No logger list file at: $LIST_FILE" >&2
    exit 2
  fi
}

main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 2
  fi

  cmd="$1"; shift || true
  case "$cmd" in
    list)
      do_list
      ;;
    status)
      [[ $# -eq 1 ]] || { usage; exit 2; }
      do_status "$1"
      ;;
    on)
      [[ $# -ge 1 ]] || { usage; exit 2; }
      logger="$1"; shift || true
      level="${1:-FINE}"
      set_level "$logger" "$level"
      echo "Enabled $logger at level=$level"
      ;;
    off)
      [[ $# -eq 1 ]] || { usage; exit 2; }
      set_level "$1" "OFF"
      echo "Disabled $1 (level=OFF)"
      ;;
    all-on)
      level="${1:-FINE}"
      if [[ ! -f "$LIST_FILE" ]]; then
        echo "Missing $LIST_FILE; cannot apply all-on." >&2
        exit 2
      fi
      while IFS= read -r logger; do
        [[ -z "$logger" ]] && continue
        set_level "$logger" "$level"
      done < "$LIST_FILE"
      echo "Enabled all listed loggers at level=$level"
      ;;
    all-off)
      if [[ ! -f "$LIST_FILE" ]]; then
        echo "Missing $LIST_FILE; cannot apply all-off." >&2
        exit 2
      fi
      while IFS= read -r logger; do
        [[ -z "$logger" ]] && continue
        set_level "$logger" "OFF"
      done < "$LIST_FILE"
      echo "Disabled all listed loggers (level=OFF)"
      ;;
    *)
      usage
      exit 2
      ;;
  esac
}

main "$@"
