# The client: Vite builds it, and nginx serves it with the API on the same
# origin. The built files run on any CPU, so they are built on the build
# machine's own platform even when the image is for another one.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# nginx passes /api/ on to the backend, so the app calls the API by path.
ARG VITE_API_URL=/api/v1
RUN npm run build

# Runs as an unprivileged user and listens on 8080.
FROM nginxinc/nginx-unprivileged:1.28-alpine
# Where nginx finds the backend; filled into the config when it starts.
ENV BACKEND_ORIGIN=http://backend:8080
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY docker/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
