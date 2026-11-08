#!/usr/bin/env bash
# Home Monitor CLI — talks to the TV local admin API (same auth as the web UI).
# Requires Home Monitor to be open on the TV (server is foreground-only).
#
#   export HM_TV=http://192.168.0.56:8765
#   export HM_TOKEN='…'   # admin token from TV first-run banner / logcat
#
# Examples:
#   ./scripts/hm-cli.sh tasks
#   ./scripts/hm-cli.sh task add --priority high --title "Renew domain"
#   ./scripts/hm-cli.sh task complete --match grocery
#   ./scripts/hm-cli.sh events --from today
#   ./scripts/hm-cli.sh event add --date 2026-11-12 --time 15:00 --title "Dentist"

set -euo pipefail

HM_TV="${HM_TV:-http://192.168.0.56:8765}"
HM_TOKEN="${HM_TOKEN:-}"

usage() {
  sed -n '2,20p' "$0"
  exit 1
}

need_token() {
  if [[ -z "$HM_TOKEN" ]]; then
    echo "Set HM_TOKEN to the Home Monitor admin token." >&2
    exit 1
  fi
}

api() {
  need_token
  local method="$1"; shift
  local path="$1"; shift
  curl -sS -X "$method" \
    -H "Authorization: Bearer ${HM_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    "${HM_TV}${path}" \
    "$@"
}

cmd="${1:-}"
shift || true

case "$cmd" in
  health)
    curl -sS "${HM_TV}/api/health" | python3 -m json.tool
    ;;
  tasks)
    api GET /api/tasks | python3 -m json.tool
    ;;
  task)
    sub="${1:-}"; shift || true
    case "$sub" in
      add)
        priority="normal"; title=""; desc=""; due=""
        while [[ $# -gt 0 ]]; do
          case "$1" in
            --priority) priority="$2"; shift 2 ;;
            --title) title="$2"; shift 2 ;;
            --description) desc="$2"; shift 2 ;;
            --due) due="$2"; shift 2 ;;
            *) echo "Unknown arg: $1" >&2; exit 1 ;;
          esac
        done
        [[ -n "$title" ]] || { echo "--title required" >&2; exit 1; }
        body=$(python3 - <<PY
import json
print(json.dumps({
  "title": """$title""",
  "priority": """$priority""",
  "description": """$desc""" or None,
  "dueDate": """$due""" or None,
}))
PY
)
        api POST /api/tasks -d "$body" | python3 -m json.tool
        ;;
      complete)
        match=""
        while [[ $# -gt 0 ]]; do
          case "$1" in
            --match) match="$2"; shift 2 ;;
            --id) id="$2"; shift 2 ;;
            *) echo "Unknown arg: $1" >&2; exit 1 ;;
          esac
        done
        if [[ -n "${id:-}" ]]; then
          api POST "/api/tasks/${id}/complete" -d '{}' | python3 -m json.tool
        else
          [[ -n "$match" ]] || { echo "--match or --id required" >&2; exit 1; }
          id=$(api GET /api/tasks | python3 -c "
import json,sys
q='''$match'''.lower()
tasks=json.load(sys.stdin).get('tasks',[])
for t in tasks:
  if t.get('status')=='pending' and q in (t.get('title') or '').lower():
    print(t['id']); break
else:
  sys.exit('No pending task matching: '+q)
")
          api POST "/api/tasks/${id}/complete" -d '{}' | python3 -m json.tool
        fi
        ;;
      reopen)
        id="${2:-}"; [[ "$1" == "--id" ]] && id="$2"
        [[ -n "$id" ]] || { echo "--id required" >&2; exit 1; }
        api POST "/api/tasks/${id}/reopen" -d '{}' | python3 -m json.tool
        ;;
      delete)
        id=""; [[ "$1" == "--id" ]] && id="$2"
        [[ -n "$id" ]] || { echo "--id required" >&2; exit 1; }
        api DELETE "/api/tasks/${id}" | python3 -m json.tool
        ;;
      *) usage ;;
    esac
    ;;
  events)
    from=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --from) from="$2"; shift 2 ;;
        *) shift ;;
      esac
    done
    if [[ "$from" == "today" ]]; then
      api GET /api/events | python3 -c "
import json,sys,datetime
today=datetime.date.today().isoformat()
events=[e for e in json.load(sys.stdin).get('events',[]) if e.get('date','')>=today]
print(json.dumps({'ok':True,'events':events}, indent=2))
"
    else
      api GET /api/events | python3 -m json.tool
    fi
    ;;
  event)
    sub="${1:-}"; shift || true
    case "$sub" in
      add)
        date=""; time=""; title=""; all_day=0; desc=""
        while [[ $# -gt 0 ]]; do
          case "$1" in
            --date) date="$2"; shift 2 ;;
            --time) time="$2"; shift 2 ;;
            --title) title="$2"; shift 2 ;;
            --all-day) all_day=1; shift ;;
            --description) desc="$2"; shift 2 ;;
            *) echo "Unknown arg: $1" >&2; exit 1 ;;
          esac
        done
        [[ -n "$date" && -n "$title" ]] || { echo "--date and --title required" >&2; exit 1; }
        body=$(python3 - <<PY
import json
all_day=bool(int("""$all_day"""))
print(json.dumps({
  "title": """$title""",
  "date": """$date""",
  "allDay": all_day,
  "startTime": None if all_day else ("""$time""" or None),
  "endTime": None,
  "description": """$desc""" or None,
}))
PY
)
        api POST /api/events -d "$body" | python3 -m json.tool
        ;;
      delete)
        id=""; [[ "$1" == "--id" ]] && id="$2"
        [[ -n "$id" ]] || { echo "--id required" >&2; exit 1; }
        api DELETE "/api/events/${id}" | python3 -m json.tool
        ;;
      *) usage ;;
    esac
    ;;
  *) usage ;;
esac
