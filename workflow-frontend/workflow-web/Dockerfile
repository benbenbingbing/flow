FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
WORKDIR /app
# 构建上下文为 workflow-frontend 目录，所有 workspace 共用一份锁文件。
COPY package*.json ./
COPY workflow-web/package.json workflow-web/package.json
COPY workflow-mobile/package.json workflow-mobile/package.json
# 包按目录整体复制，新增 biz-* / SDK 时无需维护第二份 workspace 名单。
# .dockerignore 排除宿主 node_modules/dist，容器只使用 npm ci 安装的 Linux 依赖。
COPY packages packages
RUN npm ci
COPY scripts scripts
# 字段策略生成及两端扩展编译都依赖前端工作区下的 Schema 和清单，必须随源码进入构建阶段。
COPY extensions extensions
COPY config config
COPY workflow-web workflow-web
COPY workflow-mobile workflow-mobile
# 不传参数时读取仓库配置；显式 none 构建不包含任何业务的基础产品。
ARG FLOW_FRONTEND_MODULES
RUN if [ -z "$FLOW_FRONTEND_MODULES" ]; then unset FLOW_FRONTEND_MODULES; fi; npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:44e36330f74d4f3a1d4e222acca9e23b401fb87811a7597024502bb759c4dd49
ENV SERVER_UPSTREAM=server:8080
ENV NGINX_ENVSUBST_FILTER=SERVER_UPSTREAM
COPY workflow-web/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/workflow-web/dist /usr/share/nginx/html
COPY --from=build /app/workflow-mobile/dist /usr/share/nginx/html/m
USER 101:101
EXPOSE 8080 8081
