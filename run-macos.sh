#!/usr/bin/env bash
# ChromaScape — macOS entrypoint.
#
#   ./run-macos.sh                       capturekit profile, DRY RUN (default)
#   ./run-macos.sh remoteinput           upstream frame hook (refused by the kernel on current macOS)
#   ./run-macos.sh capturekit --live     sends real input. Only after a clean dry run.
#
# Run this from a terminal, not the sidebar: the ScreenCaptureKit bridge prints its failure reason
# to stderr, and stderr is the only place it appears. If init fails, that line is the diagnosis:
#   "no SCWindow owned by pid N"   -> window enumeration; is RuneLite on screen?
#   "capture setup failed for pid" -> Screen Recording permission for the app that launched the JVM
#                                     (Terminal / iTerm / VS Code) in System Settings > Privacy & Security.
#
# The working directory is forced to this script's directory — the framework root — because every
# runtime path is relative to it.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

PROFILE=capturekit
DRY_RUN=true
for arg in "$@"; do
  case "$arg" in
    capturekit|remoteinput) PROFILE="$arg" ;;
    --live) DRY_RUN=false ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg (see --help)" >&2; exit 2 ;;
  esac
done

if [ "$(uname -s)" != "Darwin" ]; then
  echo "run-macos.sh is for macOS; use ./run-headless.sh elsewhere" >&2
  exit 1
fi

if [ "$DRY_RUN" = false ]; then
  echo "!!! LIVE RUN: real input will be sent to the RuneLite client. Ctrl-C within 5 s to abort."
  sleep 5
fi

BANNER=ENABLED; [ "$DRY_RUN" = false ] && BANNER=disabled
echo "ChromaScape macOS: profile=$PROFILE dryRun=$DRY_RUN"
echo "Confirm the banner says 'Dry-run mode: $BANNER' and 'Capture source: $PROFILE' before trusting the run."
exec ./gradlew bootRun \
  -Dchromascape.profile="$PROFILE" \
  -Dchromascape.dryRun="$DRY_RUN"
