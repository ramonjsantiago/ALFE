#!/usr/bin/env bash
set -euo pipefail

# Phase 6.5.0: Headless regression check runner.
# Builds the project (skipping tests) then runs RegressionCheckMain.

mvn -q -DskipTests package
java -cp target/classes com.fileexplorer.tools.RegressionCheckMain
