# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

# --- Serve stage: static files + reverse proxy to the API ---
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
