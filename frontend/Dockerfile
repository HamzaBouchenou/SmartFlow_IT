# §9.2 (Docker Compose imposé) et §15.2 ("construction des images Docker"). Le serveur
# d'exécution est nginx : il sert le build statique et fait aussi office de reverse proxy
# vers `api` (nginx.conf) - voir ADR-01 pour pourquoi c'est ce conteneur, et non un service
# séparé, qui porte cette responsabilité.

# --- build ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- runtime ---------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
