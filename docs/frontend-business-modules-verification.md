# 前端业务模块拆分验收

验收日期：2026-10-16。结构和后续开发约定见 [前端业务模块开发](frontend-business-modules.md)。

以下测试及产物计数记录于前端工作区迁移前，保留原验收结果。当前 npm 命令需在
`workflow-frontend/` 执行，表中的包和应用相对路径也以该目录为基准；
本记录不把目录迁移视为自动重新验收。

## 交付范围

- 项目专属模型、API 组合、PC 和移动组件统一放入 `packages/biz-project`。
- 14 份业务清单位于 `extensions/manifests/biz-project`，保留注册名、版本、配置参数和能力声明。
- SDK 公开契约和按 Vue 应用隔离的宿主能力；产品源码不能导入业务实现，业务不能导入应用私有代码。
- 清单生成、开发监听、启动脚本、生产构建及容器构建使用同一模块选择配置。
- 禁用模块后不打包其实现；页面仍引用缺失组件时明确报错，并阻止相关校验和提交。
- 表单续期功能已取消；未新增续期逻辑，过时 E2E 改为验证编辑及正常提交保留草稿和已加载发布配置。

## 测试结果

| 验证 | 结果 |
| --- | --- |
| `npm run test:architecture` | 64 项通过，覆盖源码/构建依赖边界、模块选择、开发进程重启与退出、启动脚本和容器输入 |
| `npm run test:packages` | 76 项通过，包含公共契约与宿主隔离 |
| `npm run test:business` | 14 项通过，覆盖成员变更、业务 API、真实组件运行及两端公开入口编译 |
| `npm run test:mobile` | 27 项通过 |
| `npm run test:regression --workspace workflow-web` | 功能、配置、扩展、表单与 Embed 回归通过；由完整 `test` 命令执行 |
| `npm run test:extensions:browser --workspace workflow-web` | Chrome 验收通过，包含清单新增、修改、删除后的自动刷新 |
| `npm run test:unified-components:browser --workspace workflow-web` | Chrome 验收通过，包含设计器保存、PC/移动组件、只读及回填范围 |
| `npm run test:e2e --workspace workflow-mobile -- --max-failures=1` | 37 项 Chrome E2E 全通过 |
| `npm run extensions:check --workspace workflow-web` | 56 项清单校验通过，43 项启用 |
| `npm run check:frontend-budget` | 增量预算通过，未提高历史文件预算 |
| `git diff --check` | 通过 |

PC 配置目录回归同时修复了既有说明滞后：用户关系路径、子表单按钮外观和字段脚本事件。
原严格校验断言保持不变，完整目录校验通过（455 字段、31 枚举组）。

## 生产构建及实际模块图

`FLOW_FRONTEND_MODULES=none npm run build` 和默认 `npm run build` 均通过。
检查各产物的 `module-boundary.json`，结果如下；业务模块数是构建器记录的模块数，不是扩展清单数量。

| 产物 | 默认：总模块 / 项目业务模块 | none：总模块 / 业务模块 |
| --- | --- | --- |
| PC | 2664 / 23 | 2640 / 0 |
| Embed | 1931 / 23 | 1907 / 0 |
| 移动端 | 554 / 13 | 540 / 0 |

默认产物包含对应平台的业务入口；PC/Embed 不包含移动实现，移动端不包含 PC 实现。
验收后本地构建产物恢复为默认模块配置。

## 已知验证限制

- 完整 `npm run test --workspace workflow-web` 在功能回归通过后，被旧 `test:maintainability` 阻断。
  该旧审计报告 98 个前后端文件超限；逐文件核对 HEAD 后，98 个文件均已超过对应旧预算。
  本次使用的前端增量预算通过，未放宽旧审计，也未把全量测试描述为全部通过。
- 本机 Docker daemon 未运行，因此未执行实际镜像构建。容器 COPY 输入、隔离宿主产物、默认/none
  模块选择、真实离线 `npm ci --dry-run` 和 Compose 配置校验已通过。
- 这些项目自定义表单/列表尚未在正式环境使用。未引入旧制品摘要兼容；本地锁定旧摘要的测试配置
  需重新选择组件并发布，操作要求见开发文档。

本次前端任务的数据库迁移：新增无、修改无、删除无。
