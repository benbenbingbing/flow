# Flow 移动工作台

独立 Vue 3 + Vant H5，提供待办、已办、我发起的、知会与流程详情。详情保留基本信息、流程进度、审批历史；表单 Tab 映射为可同时展开的分组，只读字段直接展示文本。用户菜单只提供退出登录。

## 启动和构建

在仓库根目录执行，Node.js 至少 22.12，npm 至少 10：

```bash
./start.sh restart  # 安装、构建公共包和两端、迁移并启动全部应用
./start.sh status
./start.sh pause    # 同 stop，停止应用和包 watch，保留数据库
```

默认 PC `http://localhost:3000`，移动端 `http://localhost:3001/m/`。
手机真机使用开发机局域网 IP，并在根 `.env` 设置 `FLOW_DEV_HOST` 后重启。
可通过 `WEB_PORT`、`MOBILE_PORT`、`SERVER_PORT` 修改端口，不能重复。

仅前端开发也从根目录安装和构建：

```bash
npm ci
npm run build:packages
npm run dev:mobile
# 单独生产构建会先构建公共包
npm run build:mobile
# 包 + PC admin/embed + mobile
npm run build
```

手动 dev 时，另行运行三个包的 `npm run dev --workspace <包名>` 监听源码；
`start.sh` 已统一管理这些进程。不要在 `workflow-web` 下生成第二份 workspace 锁文件。

## 代码放在哪里

| 位置 | 职责 |
| --- | --- |
| `workflow-mobile/src/pages` | 登录、列表、详情与审批编排 |
| `workflow-mobile/src/adapters` | 会话、Vant 提示、字段事件、上传和受控候选查询 |
| `workflow-mobile/src/extensions` | 项目验收、成员变更等移动业务扩展 |
| `packages/workflow-mobile-ui/src` | 表单、字段、子表、附件、选择器、审批面板和主题 |
| `packages/workflow-core/src` | 发布模型、校验、联动、参数、流程规则及无 UI composables |
| `packages/workflow-api/src` | 注入 request 的接口工厂；无应用 store、UI 或 token 单例 |
| `workflow-web/src` | PC Element Plus 页面、设计器与平台适配 |

只改移动样式时，修改移动应用或 `workflow-mobile-ui`；不要在 PC 的 `.el-*` 上覆盖样式。
共享规则变化需要同时验证两端。核心根入口无 Vue/DOM，可选 Vue 能力通过 `/vue/*` 子路径引入。

三个生产入口均使用 Vite 模块图检查依赖，包括懒加载模块。移动端引入 PC/Element Plus、
PC 引入移动 UI 会构建失败；产物中的 `module-boundary.json` 可检查解析结果。

## 扩展和能力限制

扩展身份仍使用原 manifest 的 `type + name + version`。在
`extensions/manifests/` 的对应清单中声明移动实现：

```json
{
  "platforms": {
    "mobile": {
      "implementation": {
        "path": "src/extensions/YourMobileForm.vue",
        "export": "default",
        "kind": "COMPONENT"
      },
      "capabilities": { "readonly": true, "editable": true, "validate": true }
    }
  }
}
```

该 path 相对 `workflow-mobile`，PC 原 `implementation` 仍相对 `workflow-web`。
`scripts/mobile-extensions.mjs` 只生成移动实现的静态导入，缺少实现不回退到 PC。
共享校验器放入 core，各平台显式注册。新增关键业务逻辑需要中文意图注释。

已适配项目验收评分/等级字段、汇总节点、项目验收整表、成员变更整表、Demo 项目整表和金额校验器。
未知扩展显示需在 PC 处理；操作依赖其编辑或校验时禁止提交。
关联内容和内嵌列表支持读取；关联写入、设计器、管理功能仍在 PC。
包含必需 `SAVE_WITH_FORM` 关联写入的表单会阻止移动提交。

提交沿用实例的发布坐标、权限和下一审批人范围。折叠字段和未打开子表行参与校验；
任务冲突时保留草稿，不自动重试审批。实例级撤回能力查询为
`GET /api/process-instance/{id}/operations`，并行分支、发起人和发布节点限制由服务端判断。

## 会话与部署

访问令牌只在内存，刷新依赖 HttpOnly Cookie。强制改密时引导至 PC，移动端不提供改密入口。
开发使用相对 `/api` 和 Vite 代理；`start.sh` 按实际主机与端口配置精确 CORS Origin。
生产前端镜像从仓库根目录构建：`docker build -f workflow-web/Dockerfile .`，
Nginx 同域提供 PC `/`、mobile `/m/`、API `/api/`；移动深链接回退 `/m/index.html`，
缺失的移动静态资源返回 404。Embed 保持独立 Origin。

## 验证

```bash
npm run test:packages
npm run test:mobile
npm run test:e2e --workspace workflow-mobile
```

浏览器用例拦截全部 API，在独立端口 43191 运行，不办理现有业务记录。
macOS 默认使用本机 Chrome；也可设置 `PLAYWRIGHT_EXECUTABLE_PATH`。
CI 安装 Playwright Chromium 后运行相同用例。
真实登录凭据仅保存在根目录被 Git 忽略的 `.env.local`，不要写入源码或测试输出。

2026-09-21 验证记录：公共包 11 项、移动状态/分组 3 项、扩展注册 13 项、浏览器 10 项、
后端节点能力 8 项均通过；完整公共包、PC/Embed、移动端生产构建通过。
统一 pause/restart/status 已实测；真实账号在 localhost 与 127.0.0.1 上完成登录、
Cookie 恢复、四类列表/详情读取和退出，CORS 预检均为 204，PC 登录和审批页也通过。
审批、驳回、转办、撤回、重新提交使用隔离 API 的浏览器用例验证，未对现有业务记录执行审批写入。

PC 原回归中有 11 个基线失败项，已与设计基线比较，包括配置帮助、审批校验、运行时诊断、
实体选择、列表作用域、Embed native-list、功能热修复、页面配置、配置引用、UI 配置和可维护性预算。
本次未修复这些既有问题；PC/Embed 生产构建与其他相关回归继续执行。

本次数据库迁移：新增无、修改无、删除无。
