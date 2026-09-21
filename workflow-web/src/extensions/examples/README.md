# 扩展示例使用说明

`demo/` 展示项目表单、卡片列表与风险单元格；`contracts/` 展示契约调用、校验器、动作和节点配置升级。注册示例全部位于 `manifests/examples/`，本说明统一覆盖这些子目录。

## 启用方式

开发服务器 `npm run dev` 下，现有主应用和 Embed 因 `import.meta.env.DEV` 自动开启 Demo。生产构建默认不安装示例；需要演示构建时从 `workflow-web/` 运行：

```sh
VITE_ENABLE_DEMO_EXTENSIONS=true npm run build
```

`examples/demo` 的三个清单已经 enabled=true。`examples/contracts` 的清单默认 enabled=false，需先把要试用的那一份改为 true；仅打开 Demo 环境变量不会启用禁用清单。运行开关控制安装，不表示相关代码一定从产物中剔除。

## 使用三个 Demo

| 实现文件 | 设计器选择及参数 | 查看效果 |
| --- | --- | --- |
| demo/forms/DemoProjectForm.vue | 整表单选择 DemoProjectForm；subtitle、accentColor、showRiskHint | 自定义布局、只读/联动、表单校验 |
| demo/lists/DemoProjectCardList.vue | 整列表选择 DemoProjectCardList；columns、compact、showDescription、searchPlaceholder | 卡片布局、查询、分页与宿主动作 |
| demo/list-fields/DemoRiskProgressCell.vue | 数值列选择 DemoRiskProgressCell；warningAt=40、dangerAt=70、showText、showLevel | 数值变化时的风险等级和进度条 |

先绑定已有实体的表单/列表配置，再保存预览。组件不会创建实体或数据，审批、删除等操作仍由宿主和后端控制。

## 契约示例文件怎么用

| 文件 | 用法 |
| --- | --- |
| contracts/AmountValidator.js | 导入 AmountValidator 或 requiredValidator；前者复用通用类，后者演示对象校验 |
| contracts/ValidatedAmountForm.vue | 导入到自己的 Vue 页面并挂载，直接体验 Element Plus 校验；独立组件不自动加入实体设计器 |
| contracts/validatorFactory.js | 导入 createTextLengthValidator，或启用 contract_text_length 的 FACTORY 清单 |
| contracts/refreshAction.js | 启用 contractExampleRefresh 清单，列表按钮设为自定义/函数，处理器填该名称 |
| contracts/permissions.js | 启用 contractExamplePermissionOptions 清单，权限编辑器查询当前上下文时调用候选提供器 |
| contracts/nodeConfig.js | 在 NODE 的 hooks.migrateConfig 中引用 migrateSummaryConfig；ContractExampleNode 清单已有配置 |

例如在项目 JS 模块中直接体验工厂与迁移：

```js
import { createTextLengthValidator } from '@/extensions/examples/contracts/validatorFactory.js'
import { migrateSummaryConfig } from '@/extensions/examples/contracts/nodeConfig.js'

const validator = createTextLengthValidator({ services: {} })
const result = validator.validate('你好世界', { params: { maxLength: 3 } })
// result 为“文本不能超过 3 个字符”；实际提交用 validateCustomValue 归一化。
const next = migrateSummaryConfig({ fromVersion: 1, toVersion: 2, config: { fieldCode: 'amount' } })
// next 为 { fieldCodes: ['amount'] }，旧配置不被修改。
```

启用 `ContractExampleRelatedForm` 或 `ContractExampleRelatedList` 后，只能在关联内容的自定义呈现中选择，不能当作普通整表单/列表使用；这些清单声明 RELATED_CONTENT 契约。

## 复制到正式模块与验证

将实现复制到 business/<module> 或 common，复制对应清单到相同归属，修改 name、path、label、范围和参数，并设置 enabled=true。正式业务清单不依赖 Demo 开关；不要与原示例使用相同的活动注册名。

从 `workflow-web/` 执行 `npm run extensions:check`、`npm run test:extension-contracts`。`npm run test:extensions:browser` 验证 Demo 开关和真实注册；需要验证校验交互时运行 `npm run test:custom-validators:browser`。
