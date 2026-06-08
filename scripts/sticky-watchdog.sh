#!/usr/bin/env bash
# =============================================================================
# Sticky self-healing watchdog
# -----------------------------------------------------------------------------
# Checks every public Sticky endpoint. If one is down it tries to recover it
# automatically (start a stopped container, force a Traefik reload, restart the
# backing container as a last resort) and only pings ntfy when a service's
# state actually CHANGES (down / recovered) — so there is no alert spam.
#
# Installed on the server as a systemd timer that runs every 2 minutes.
# Notifications go to the self-hosted ntfy topic `sticky-health`; subscribe at
# https://sticky-ntfy.46.225.95.201.sslip.io/sticky-health from any browser.
#
# Root cause this guards against: Traefik file-provider routers that used to
# hard-code container IPs and returned 502 after a redeploy reshuffled IPs.
# See the traefik-routing-gotcha project memory.
# =============================================================================
set -uo pipefail

NTFY="http://localhost:8089/sticky-health"
STATE_DIR=/opt/sticky-watchdog/state
LOG=/opt/sticky-watchdog/watchdog.log
DYNAMIC_DIR=/data/coolify/proxy/dynamic

mkdir -p "$STATE_DIR"

log()    { echo "$(date '+%F %T') $*" >> "$LOG"; }
notify() { # title, message, priority, tags
  curl -s -o /dev/null --max-time 8 \
    -H "Title: $1" -H "Priority: ${3:-default}" -H "Tags: ${4:-warning}" \
    -d "$2" "$NTFY" || true
}
code_of()        { curl -sk -o /dev/null -m 12 -w '%{http_code}' "$1" 2>/dev/null; }
find_container() { docker ps -a --format '{{.Names}}' | grep -m1 "^$1" || true; }

# ---------------------------------------------------------------------------
# Orphan guard: the admin/worker hosts are served by the Coolify-managed
# containers (names like `sticky-admin-<suffix>`). An old manual deploy can
# leave a bare-named orphan (`sticky-admin`, `sticky-worker`) bound to the SAME
# Traefik host rule. Traefik then round-robins between a fresh build and a
# stale one, so the panel loads only on some refreshes (stale JS chunk 404s).
# If both a bare orphan AND a Coolify-managed sibling are running, drop the
# orphan so a single container owns the host.
# ---------------------------------------------------------------------------
kill_orphans() {
  for base in sticky-admin sticky-worker; do
    bare=$(docker ps --format '{{.Names}}' | grep -xc "$base" || true)
    managed=$(docker ps --format '{{.Names}}' | grep -c "^${base}-" || true)
    if [ "$bare" -ge 1 ] && [ "$managed" -ge 1 ]; then
      docker rm -f "$base" >/dev/null 2>&1 || true
      log "removed stale orphan container '$base' (Coolify-managed sibling present)"
      notify "Auto-healed: removed orphan $base" \
        "A duplicate '$base' container was round-robining with the live one and has been removed." \
        default wrench
    fi
  done
}

# Deep check for the admin SPA: index.html can return 200 while the hashed JS
# chunk it points at 404s (the exact 'spins forever, loads on Nth refresh'
# symptom). Returns 200 only if BOTH the page and its JS bundle load.
admin_spa_code() {
  local base="https://sticky-admin.46.225.95.201.sslip.io"
  local html js jscode
  html=$(curl -sk -m 12 "$base/?cb=$RANDOM" 2>/dev/null)
  [ -z "$html" ] && { echo 000; return; }
  js=$(printf '%s' "$html" | grep -oE '/static/index-[A-Za-z0-9_-]+\.js' | head -1)
  [ -z "$js" ] && { echo 521; return; }   # 521 = html ok but no JS reference
  jscode=$(code_of "$base$js")
  echo "$jscode"
}

# Probe the current check ($name/$url are set by the loop). Admin uses the deep
# SPA check; everything else is a plain status check.
probe() {
  if [ "$name" = "admin" ]; then admin_spa_code; else code_of "$url"; fi
}

# name | url | want | container-name-prefix | mode(full|soft)
#   full = safe to start/restart the backing container automatically (images, ntfy)
#   soft = Coolify-managed; only start if exited, never auto-restart a running one
CHECKS=(
  "images|https://sticky-images.46.225.95.201.sslip.io/stickers/3d_party_/1771939538542_3.webp|200|sticky-images|full"
  "ntfy|https://sticky-ntfy.46.225.95.201.sslip.io/|200|sticky-ntfy|full"
  "admin|https://sticky-admin.46.225.95.201.sslip.io/|200|sticky-admin-|soft"
  "pocketbase|https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io/api/health|200|sh3xlf9j7symlj3otlw6s8rx-|soft"
)

# Clean up duplicate/orphan containers before the endpoint checks.
kill_orphans

for entry in "${CHECKS[@]}"; do
  IFS='|' read -r name url want match mode <<< "$entry"

  # 3 attempts so a transient network blip never triggers remediation
  code=000
  for _ in 1 2 3; do
    code=$(probe)
    [ "$code" = "$want" ] && break
    sleep 3
  done

  state_file="$STATE_DIR/$name"
  prev=$(cat "$state_file" 2>/dev/null || echo UP)

  if [ "$code" = "$want" ]; then
    if [ "$prev" = "DOWN" ]; then
      log "$name RECOVERED ($code)"
      notify "OK: $name recovered" "$url is healthy again ($code)" default white_check_mark
    fi
    echo UP > "$state_file"
    continue
  fi

  log "$name DOWN (got '$code', want '$want')"
  cont=$(find_container "$match")
  action="none"

  if [ -n "$cont" ]; then
    st=$(docker inspect -f '{{.State.Status}}' "$cont" 2>/dev/null || echo unknown)

    if [ "$st" != "running" ]; then
      # Container is stopped/exited/dead -> safe to start in any mode
      docker start "$cont" >/dev/null 2>&1 && action="started $cont"
    elif [ "$mode" = "full" ]; then
      # Running but unreachable: gentlest first — force Traefik to re-read
      # the dynamic configs (re-resolves the DNS-name backend), then recheck.
      touch "$DYNAMIC_DIR"/*.yaml 2>/dev/null || true
      sleep 4
      if [ "$(probe)" = "$want" ]; then action="traefik-reload"; fi
      if [ "$action" = "none" ]; then
        docker restart "$cont" >/dev/null 2>&1 && action="restarted $cont"
      fi
    fi

    if [ "$action" != "none" ]; then
      sleep 4
      if [ "$(probe)" = "$want" ]; then
        log "$name auto-healed via: $action"
        notify "Auto-healed: $name" "Recovered after: $action" default wrench
        echo UP > "$state_file"
        continue
      fi
      log "$name still down after: $action"
    fi
  fi

  # Still down — alert once, only on the UP->DOWN transition
  if [ "$prev" != "DOWN" ]; then
    notify "DOWN: $name" "$url returned $code. Tried: ${action}. May need a manual look." high rotating_light
  fi
  echo DOWN > "$state_file"
done
