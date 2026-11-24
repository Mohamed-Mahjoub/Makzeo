# --- Étape 1 : compilation Angular ------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# --- Étape 2 : serveur nginx non-root ----------------------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine
LABEL org.opencontainers.image.title="makzeo-frontend" \
      org.opencontainers.image.description="Makzeo - interface web" \
      org.opencontainers.image.vendor="Makzeo"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist/stock-management-frontend/browser /usr/share/nginx/html
EXPOSE 8080
