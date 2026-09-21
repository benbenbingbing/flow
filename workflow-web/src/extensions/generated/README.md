# 自动生成字段策略

`field-definitions.js` 由平台 FIELD 清单生成，保存 name、aliases、defaultForFieldTypes、supportedFieldTypes。它用于无 Vue 的字段策略和 Node 测试，不保存组件实现。

## 如何更新

1. 修改 `manifests/platform/fields/*.extension.json` 中对应字段的别名、兼容类型或默认映射。
2. 在 `workflow-web/` 执行：

```sh
npm run extensions:generate
npm run extensions:check
```

3. 检查源清单和本文件的差异，把两者一起提交。不能只改生成文件，否则下次生成会覆盖。

`extensions:check` 只校验一致性，发现过期时提示运行 generate；Vite 启动和构建也会更新生成结果。新增业务字段不需要编辑此文件。

## 如何读取

调用方通常使用 `core/fieldPolicy.js`，不用依赖生成结构。例如以下命令从 `workflow-web/` 执行，输出 `textarea`：

```sh
node --input-type=module -e "import { getDefaultFormFieldComponentType } from './src/extensions/core/fieldPolicy.js'; console.log(getDefaultFormFieldComponentType('TEXT'))"
```

确需枚举纯数据时可以 `import definitions from '@/extensions/generated/field-definitions.js'`。读取这些数据不会完成组件注册；运行宿主仍由应用入口安装扩展。
