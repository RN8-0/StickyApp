FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./

# Build-time env vars - Coolify'dan build arg olarak geçirilmeli
ARG VITE_PB_URL
ARG VITE_PB_PUBLIC_URL
ARG VITE_APP_URL
ARG VITE_OAUTH_REDIRECT_URL
ARG VITE_WORKER_URL
ARG VITE_IMAGE_PROXY_URL
ENV VITE_PB_URL=$VITE_PB_URL
ENV VITE_PB_PUBLIC_URL=$VITE_PB_PUBLIC_URL
ENV VITE_APP_URL=$VITE_APP_URL
ENV VITE_OAUTH_REDIRECT_URL=$VITE_OAUTH_REDIRECT_URL
ENV VITE_WORKER_URL=$VITE_WORKER_URL
ENV VITE_IMAGE_PROXY_URL=$VITE_IMAGE_PROXY_URL

RUN npm run build

# Bundle FFmpeg WASM locally so it loads from our server (fast) instead of CDN
RUN npm install --no-save @ffmpeg/core@0.12.6 && \
    cp node_modules/@ffmpeg/core/dist/esm/ffmpeg-core.js dist/ && \
    cp node_modules/@ffmpeg/core/dist/esm/ffmpeg-core.wasm dist/

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chmod -R a+rX /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
