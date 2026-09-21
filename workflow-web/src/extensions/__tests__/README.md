# 扩展测试怎么运行

所有命令从 `workflow-web/` 执行；先按项目方式安装 package.json 中的依赖。Node 测试不需要业务后端，浏览器验收使用独立 Chrome 和模拟数据，不创建业务记录。

## 按改动选择验证

| 改动 | 命令 | 覆盖内容 |
| --- | --- | --- |
| 新增/修改 JSON | npm run extensions:check | Schema、路径、类型、冲突与生成数据一致性 |
| 注册核心或清单行为 | npm run test:extensions | 本目录全部 spec.mjs |
| 契约、模板或契约示例 | npm run test:extension-contracts | 校验契约测试、JS/Vue 实际编译 |
| 新业务 Vue / JS 实现 | npm run build | 主应用和 Embed 构建，真实模块导出 |
| 清单自动发现或热更新 | npm run test:extensions:browser | 新增/修改/删除 JSON 后页面刷新与注册 |
| 字段校验交互 | npm run test:custom-validators:browser | 表单、子表、审批、只读与错误提示 |

## 文件怎么用

- registration.spec.mjs：发现器、安装器、Demo、字段解析和真实金额规则的回归。新增安装行为时在此增加对应场景。
- catalog-and-contract.spec.mjs：目录投影、治理边界、宿主过滤和旧入口删除。变更查询/过滤时参考这些用例。
- build.spec.mjs：真实 Vite 验证缺失具名导出和仅改 JSON 后恢复，以及可复制工厂/迁移示例。
- helpers/install-node-extensions.mjs：Node 测试从正式 JSON 安装校验器；仅供测试，不从生产入口导入。

仅运行一个测试文件：

```sh
node --test src/extensions/__tests__/registration.spec.mjs
```

## 浏览器验收

```sh
CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm run test:extensions:browser
```

脚本启动本机 Vite 和独立无头 Chrome，在临时目录创建实现和 JSON，测试结束自动清理。环境需允许启动本机端口和浏览器；安装路径不同则修改 CHROME_PATH。PASS 表示该测试覆盖的模拟场景通过，不能代替真实实体/流程的后端发布验收。

## 常见失败

生成数据过期时先改源清单、运行 extensions:generate；缺失导出检查 implementation.export；别名/身份冲突检查同名启用清单。修复后重跑相关测试。现有默认 npm test 的历史失败记录见仓库 docs/frontend-extension-json-registration-verification.md，不要为让本次检查通过而删除无关断言。
