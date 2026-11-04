FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=backend:8080
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
