# ===== Stage 1: Build =====
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Vite build-time env vars (pass via Coolify build args)
ARG VITE_APP_URL
ARG VITE_PB_URL
ARG VITE_PB_PUBLIC_URL
ARG VITE_WORKER_URL
ARG VITE_IMAGE_PROXY_URL
ARG VITE_OAUTH_REDIRECT_URL

RUN npm run build

# ===== Stage 2: Serve =====
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chmod -R a+rX /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
