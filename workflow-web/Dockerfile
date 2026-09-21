FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
WORKDIR /app
# 构建上下文为仓库根目录，所有 workspace 共用一份锁文件。
COPY package*.json ./
COPY workflow-web/package.json workflow-web/package.json
COPY workflow-mobile/package.json workflow-mobile/package.json
COPY packages/workflow-core/package.json packages/workflow-core/package.json
COPY packages/workflow-api/package.json packages/workflow-api/package.json
COPY packages/workflow-mobile-ui/package.json packages/workflow-mobile-ui/package.json
RUN npm ci
COPY scripts scripts
COPY packages packages
COPY workflow-web workflow-web
COPY workflow-mobile workflow-mobile
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:44e36330f74d4f3a1d4e222acca9e23b401fb87811a7597024502bb759c4dd49
ENV SERVER_UPSTREAM=server:8080
ENV NGINX_ENVSUBST_FILTER=SERVER_UPSTREAM
COPY workflow-web/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/workflow-web/dist /usr/share/nginx/html
COPY --from=build /app/workflow-mobile/dist /usr/share/nginx/html/m
USER 101:101
EXPOSE 8080 8081
