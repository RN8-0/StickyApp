#!/bin/bash
set -e

# =============================================
# sticky-admin HTTPS deployment via Coolify/Traefik
# Domain: sticky-admin.46.225.95.201.sslip.io
# =============================================

PRIVACY_DOMAIN="sticky-privacy.46.225.95.201.sslip.io"

# NOTE: sticky-admin is NO LONGER deployed here. The admin panel is built and
# deployed by Coolify from sticker_admin_web/Dockerfile (container name
# `sticky-admin-<suffix>`). Creating a second manual `sticky-admin` container
# here would bind the SAME Traefik host rule and make the panel load only on
# some refreshes (Traefik round-robins a fresh build against a stale one). The
# watchdog (scripts/sticky-watchdog.sh) auto-removes such an orphan, but don't
# recreate it in the first place. This script now only manages sticky-privacy.

chmod -R a+rX /opt/sticky-privacy 2>/dev/null || true

# --- sticky-privacy ---
docker rm -f sticky-privacy 2>/dev/null || true

docker run -d \
  --name sticky-privacy \
  --network coolify \
  -v /opt/sticky-privacy:/usr/share/nginx/html:ro \
  --restart unless-stopped \
  --label "traefik.enable=true" \
  --label "traefik.http.middlewares.gzip.compress=true" \
  --label "traefik.http.routers.http-0-sticky-privacy.entryPoints=http" \
  --label "traefik.http.routers.http-0-sticky-privacy.middlewares=gzip" \
  --label "traefik.http.routers.http-0-sticky-privacy.rule=Host(\`${PRIVACY_DOMAIN}\`) && PathPrefix(\`/\`)" \
  --label "traefik.http.routers.http-0-sticky-privacy.service=http-0-sticky-privacy" \
  --label "traefik.http.services.http-0-sticky-privacy.loadbalancer.server.port=80" \
  nginx:alpine

echo "sticky-privacy started"

# --- Traefik HTTPS dynamic config for sticky-privacy ---
cat > /data/coolify/proxy/dynamic/sticky-privacy-https.yaml << EOF
http:
  routers:
    sticky-privacy-https:
      entryPoints:
        - https
      service: http-0-sticky-privacy@docker
      rule: Host(\`${PRIVACY_DOMAIN}\`)
      tls:
        certresolver: letsencrypt
    sticky-privacy-http-redirect:
      entryPoints:
        - http
      rule: Host(\`${PRIVACY_DOMAIN}\`)
      middlewares:
        - redirect-to-https
      service: http-0-sticky-privacy@docker
EOF

echo ""
echo "=== Deployment complete ==="
echo "Privacy page: https://${PRIVACY_DOMAIN}"
echo ""
echo "Waiting 5s for Traefik to pick up changes..."
sleep 5
curl -sk -o /dev/null -w "Privacy Page HTTP status: %{http_code}\n" "https://${PRIVACY_DOMAIN}/" || true
