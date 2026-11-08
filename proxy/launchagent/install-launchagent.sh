#!/usr/bin/env bash
# Install a KeepAlive LaunchAgent for the Home Monitor WF1 read-only proxy.
# Does not modify space.cactusinc.sudoku-gym.wf1-monitor.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL="space.cactusinc.home-monitor.wf1-proxy"
PLIST_DST="$HOME/Library/LaunchAgents/${LABEL}.plist"
LOG_DIR="$HOME/Library/Logs/home-monitor"
TOKEN="${HM_TOKEN:-}"

if [[ -z "$TOKEN" || "$TOKEN" == "change-me-home-monitor-token" ]]; then
  echo "Set HM_TOKEN to a strong shared secret before installing." >&2
  echo "Example: HM_TOKEN=\$(openssl rand -hex 24) $0" >&2
  exit 1
fi

mkdir -p "$LOG_DIR" "$HOME/Library/LaunchAgents"

sed \
  -e "s|__PROXY_SERVER__|${ROOT}/server.mjs|g" \
  -e "s|__PROXY_DIR__|${ROOT}|g" \
  -e "s|__HM_TOKEN__|${TOKEN}|g" \
  -e "s|__LOG_DIR__|${LOG_DIR}|g" \
  "$(dirname "$0")/${LABEL}.plist.template" > "$PLIST_DST"

launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"
launchctl enable "gui/$(id -u)/${LABEL}" 2>/dev/null || true
launchctl kickstart -k "gui/$(id -u)/${LABEL}" 2>/dev/null || launchctl load "$PLIST_DST"

echo "Installed $PLIST_DST"
echo "Health: curl -s http://127.0.0.1:8792/v1/health"
echo "Queue:  curl -s -H \"Authorization: Bearer \$HM_TOKEN\" http://127.0.0.1:8792/v1/wf1/queue"
