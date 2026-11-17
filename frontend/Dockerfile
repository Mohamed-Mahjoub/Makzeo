# --- Étape 1 : build Angular -------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
RUN npx ng build --configuration production

# --- Étape 2 : serveur Nginx non-root ----------------------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine
LABEL org.opencontainers.image.title="frontend" \
      org.opencontainers.image.description="Stock Management - frontend Angular"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/stock-management-frontend/browser /usr/share/nginx/html
EXPOSE 8080
