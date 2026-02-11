#!/usr/bin/env bash
set -euo pipefail
mvn -U org.openjfx:javafx-maven-plugin:0.0.8:run "$@"
