#!/bin/bash
set -e

# =============================================
# sticky-admin HTTPS deployment via Coolify/Traefik
# Domain: sticky-admin.46.225.95.201.sslip.io
# =============================================

ADMIN_DOMAIN="sticky-admin.46.225.95.201.sslip.io"
PRIVACY_DOMAIN="sticky-privacy.46.225.95.201.sslip.io"

chmod -R a+rX /opt/sticky-admin 2>/dev/null || true
chmod -R a+rX /opt/sticky-privacy 2>/dev/null || true

# --- sticky-admin ---
docker rm -f sticky-admin 2>/dev/null || true

docker run -d \
  --name sticky-admin \
  --network coolify \
  -v /opt/sticky-admin:/usr/share/nginx/html:ro \
  --restart unless-stopped \
  --label "traefik.enable=true" \
  --label "traefik.http.middlewares.gzip.compress=true" \
  --label "traefik.http.routers.http-0-sticky-admin.entryPoints=http" \
  --label "traefik.http.routers.http-0-sticky-admin.middlewares=gzip" \
  --label "traefik.http.routers.http-0-sticky-admin.rule=Host(\`${ADMIN_DOMAIN}\`) && PathPrefix(\`/\`)" \
  --label "traefik.http.routers.http-0-sticky-admin.service=http-0-sticky-admin" \
  --label "traefik.http.services.http-0-sticky-admin.loadbalancer.server.port=80" \
  nginx:alpine

# Also connect to sticky-net for PB access
docker network connect sticky-net sticky-admin

echo "sticky-admin started"

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

# --- Traefik HTTPS dynamic config for sticky-admin ---
cat > /data/coolify/proxy/dynamic/sticky-admin-https.yaml << EOF
http:
  routers:
    sticky-admin-https:
      entryPoints:
        - https
      service: http-0-sticky-admin@docker
      rule: Host(\`${ADMIN_DOMAIN}\`)
      tls:
        certresolver: letsencrypt
    sticky-admin-http-redirect:
      entryPoints:
        - http
      rule: Host(\`${ADMIN_DOMAIN}\`)
      middlewares:
        - redirect-to-https
      service: http-0-sticky-admin@docker
  middlewares:
    redirect-to-https:
      redirectScheme:
        scheme: https
        permanent: true
EOF

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
echo "Admin panel: https://${ADMIN_DOMAIN}"
echo "Privacy page: https://${PRIVACY_DOMAIN}"
echo ""
echo "Waiting 5s for Traefik to pick up changes..."
sleep 5
curl -sk -o /dev/null -w "Admin Panel HTTP status: %{http_code}\n" "https://${ADMIN_DOMAIN}/" || true
curl -sk -o /dev/null -w "Privacy Page HTTP status: %{http_code}\n" "https://${PRIVACY_DOMAIN}/" || true
