# JSON 清单构建器

路径说明：前端工作区根目录为仓库中的 `workflow-frontend/`，npm 依赖统一在该目录安装；包和清单的相对关系不变。

本目录的 discover/validate/generate 是兼容入口，实际中立实现位于前端工作区根目录 `scripts/extensions/`，共享清单位于前端工作区根目录 `extensions/manifests/`。PC 专属 Vite 适配器仍在本目录。

`discover.mjs` 扫描集中清单目录，按来源路径确定平台、通用、业务或示例归属。`validate.mjs` 读取同一份 JSON Schema 并校验名称、参数、源码路径、同名业务包公开入口和最终注册冲突，不执行业务模块。

`scripts/extensions/modules.mjs` 读取前端工作区根目录 config/frontend-modules.json 或 FLOW_FRONTEND_MODULES 环境覆盖。未选业务只检查声明结构，不解析实现文件，不生成导入；基础产品构建使用 FLOW_FRONTEND_MODULES=none。

`generate.mjs` 生成静态 import 的虚拟模块，以及供纯 JS 规则使用的字段策略。`vite-plugin.mjs` 同时接入 admin/embed 构建，监听清单及模块选择配置增删改并整页刷新，避免注册状态在 HMR 中累积。

命令从 workflow-web 执行：

```sh
npm run extensions:generate
npm run extensions:check
npm run test:extensions
npm run build
```

新增模块不需要修改本目录。只有新增一种平台扩展类型时，才同步扩展 Schema、适配器、宿主运行契约及测试。
