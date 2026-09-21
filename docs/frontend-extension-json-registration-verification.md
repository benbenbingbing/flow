# JSON 扩展集中化实施验收

日期：2026-09-21。迁移对照版本：`48273ad7`。范围为前端源码、构建注册、管理页、指南、示例及测试；未连接真实业务数据库执行发布或生产数据验收。

## 交付内容

- 十类扩展：FORM、FIELD、NODE、LIST、LIST_CELL、LIST_BUTTON、LIST_ACTION、VALIDATOR、ACTION_CONDITION、PERMISSION_PROVIDER。
- 53 份清单，40 份启用；Demo 关闭时实际安装 37 项，打开后增加 3 项。其余 13 份为默认禁用的契约模板/示例。
- 现有业务实现、平台字段和单元格、金额校验、Demo、契约和模板迁入 `workflow-web/src/extensions`。旧目录和注册入口已删除，无重导出兼容层。
- 主应用与 Embed 使用同一构建插件与注册入口；生产注册 API 调用只位于 core/adapters。
- JSON Schema、路径、身份/版本/别名冲突检查，静态导入生成，导出形态校验，安装幂等，清单增删改刷新。
- 管理页展示全部类型；平台必需项和未实现后端治理的类型显示“仅构建注册”。
- 普通页面与关联内容按 usageContexts 匹配；配置候选、版本候选、运行装载共同过滤。
- 12 份目录 README（仅 extensions 根目录及下一层，子目录用法已合并）；契约属性、模板使用方法和关键注册逻辑补充中文注释。

原注册入口提取的 31 条声明（双位置动作各计两条）与新清单逐项比较，名称、版本和原有元数据均保留。契约示例节点另补充了 snapshotVersion=2 的升级示例；实际业务节点保持原配置版本。

## 已通过

| 验证 | 结果 |
| --- | --- |
| `npm run extensions:check` | 53 份清单结构、路径、冲突与生成数据一致性通过 |
| `npm run test:extensions` | 13 项通过；含真实 Vite 缺失具名导出失败及仅修正 JSON 后恢复 |
| `npm run test:extension-contracts` | 8 项校验契约测试通过；28 个契约、模板、示例 JS/Vue 入口真实编译通过 |
| `npm run test:extensions:browser` | 十类实际注册、字段/单元格渲染、动作引用、金额校验、权限去重、Demo 开关通过；新增/修改/删除临时 JSON 后自动刷新通过 |
| `npm run test:custom-validators:browser` | 实体表单、平铺/节点/自绘表单、子表、编辑器、审批、只读、隐藏字段、错误提示和失焦行为通过 |
| `npm run test:entity-relations` | 关联内容、关系模型、运行参数及页面参数检查通过 |
| `node scripts/embed-boundary-test.mjs` | 主应用与 Embed 共享 JSON 入口、原运行边界通过 |
| `node src/shared/__tests__/list-field-extension-guide.spec.js` | 指南与 JSON 示例、字段协议检查通过 |
| `npm run build` | admin 与 Embed 两种生产构建通过 |
| `git diff --check` | 通过 |

浏览器测试使用独立无头 Chrome 与临时组件/JSON；测试结束清理文件，不创建业务数据。可通过 `CHROME_PATH` 指定本机浏览器。构建仍报告已有的大包、静态/动态导入重叠等警告。

## 现有全量测试情况

将 package.json 中默认 test 链递归展开，逐项执行了 113 条独立检查，避免前面的历史失败遮蔽后续结果：102 项通过，11 项失败。另执行上述新增扩展测试与浏览器验收。

以下失败均在迁移前 `48273ad7` 的独立源码目录中复现，首个失败原因一致；没有将它们改为跳过或放宽断言。默认 `npm test` 因这些历史失败仍不会全绿。

| 失败检查 | 迁移前后共同的失败点 |
| --- | --- |
| config-field-help.spec.js | entityList.queryInterfaceExtension 帮助项断言 |
| approvalFormValidation.spec.js | FormPreview 首个唯一错误暴露接口断言 |
| runtime-diagnostics.spec.js | 默认列表工具栏排障扩展位置断言 |
| entity-definition-selection.spec.js | 关联子实体的已发布动态实体约束断言 |
| list-scope-binding.spec.js | 表单事件保存后的草稿/发布差异刷新断言 |
| embed-native-list-reload-test.mjs | 普通页面列表错误展示断言 |
| functional.spec.js | 已移除 uiHotfixGovernance.apply 的旧接口断言 |
| page-config-audit.spec.js | “节”作为添加节点菜单类型的断言 |
| configuration-reference.spec.js | relativePosition.anchor 的说明被判过于泛化 |
| ui-config-audit.mjs | 现有模板片段缺少闭合标签 |
| maintainability-budget.mjs | 既有前后端文件超过行数预算；前端超标文件仍为 28 个，迁移后无新增超标文件 |

迁移相关的旧注册路径断言已更新为新 JSON/适配器行为，列表字段指南与 Embed 边界检查恢复通过。测试不包含真实后端发布快照、生产权限或生产数据验收。FORM/LIST 自动 artifactDigest 的原算法保留，源码搬迁不保证旧自动摘要相同，正式发布前应核对已有摘要锁定配置。

## 数据库与工作区

Flyway 迁移文件：新增无、修改无、删除无。已检查 Git 差异，本次未改动任何 SQL 迁移；未执行 flyway repair 或基线操作。

源码、清单、文档和测试保留在当前工作区，未提交、未部署。开发者操作步骤和完整目录说明见 [实施设计](frontend-extension-json-registration-design.md) 与 [扩展中心 README](../workflow-web/src/extensions/README.md)。
