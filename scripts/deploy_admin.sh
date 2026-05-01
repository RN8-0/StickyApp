#!/bin/bash
set -e

docker rm -f sticky-admin 2>/dev/null || true
mkdir -p /opt/sticky-admin
cp -r /tmp/sticky-admin-dist/* /opt/sticky-admin/
chmod -R a+rX /opt/sticky-admin

cat > /opt/sticky-admin.conf << 'NGINXEOF'
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    gzip on;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;
    location /assets/ {
        try_files $uri =404;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    location / {
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://sh3xlf9j7symlj3otlw6s8rx-162704953376:8090/api/;
        proxy_set_header Host sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
NGINXEOF

docker run -d \
  --name sticky-admin \
  --network sticky-net \
  -p 8085:80 \
  -v /opt/sticky-admin:/usr/share/nginx/html:ro \
  -v /opt/sticky-admin.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:alpine

echo "=== Admin panel deployed on port 8085 ==="
docker ps | grep sticky-admin
