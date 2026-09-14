#!/usr/bin/env bash
# ChromaScape — headless entrypoint (replay profile).
#
# Runs the whole framework with no client, no display and no native library, on any OS with a
# JDK 17: frames come from PNG fixtures, input goes to a headless device that records instead of
# sends. See docs/reference/headless-replay.md.
#
#   ./run-headless.sh                    dry run; fixtures from ../calibration/fixtures
#   FIXTURES=/path/to/frames ./run-headless.sh
#   ./run-headless.sh --live             dry-run OFF: gated actions execute against the headless device
#
# Environment:
#   GRADLE_BIN            a gradle binary to use instead of ./gradlew (offline hosts)
#   CHROMASCAPE_OFFLINE=1 pass --offline to Gradle
#   JAVA_HOME             honoured by Gradle as usual
#
# The working directory is forced to this script's directory — the framework root — because every
# runtime path (colours/colours.json, output/, the scripts tree, both native binaries) is relative.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

FIXTURES="${FIXTURES:-../calibration/fixtures}"
DRY_RUN=true
for arg in "$@"; do
  case "$arg" in
    --live) DRY_RUN=false ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg (see --help)" >&2; exit 2 ;;
  esac
done

if [ ! -d "$FIXTURES" ]; then
  echo "fixture directory not found: $FIXTURES (set FIXTURES=...)" >&2
  exit 1
fi
if ! ls "$FIXTURES"/*.png >/dev/null 2>&1; then
  echo "fixture directory has no .png files: $FIXTURES" >&2
  exit 1
fi

GRADLE="${GRADLE_BIN:-./gradlew}"
OFFLINE=()
if [ "${CHROMASCAPE_OFFLINE:-0}" = "1" ]; then OFFLINE=(--offline); fi

echo "ChromaScape headless: profile=replay fixtures=$FIXTURES dryRun=$DRY_RUN"
echo "Web UI: http://localhost:8080  — confirm the banner says 'Capture source: replay'"
exec "$GRADLE" "${OFFLINE[@]}" bootRun \
  -Dchromascape.profile=replay \
  -Dchromascape.replay.fixtures="$FIXTURES" \
  -Dchromascape.dryRun="$DRY_RUN"
