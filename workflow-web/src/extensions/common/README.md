# 通用扩展实现

存放不依赖某个业务实体的实现。当前已有 [金额校验器](#通用校验器)；其安装声明位于 `manifests/common/validators/`。

## 如何复用已有实现

在设计器对应的扩展选项中选择注册名，例如字段自定义校验选择 `amount@1`，配置当前字段的额度参数。注册表共享实现，参数由每个字段独立保存；不需要复制 AmountValidator 或重新注册。

独立 Vue 页面也可直接调用实现和契约辅助方法，示例见 [校验器用法](#通用校验器)。直接导入只调用代码，不会把该页面加入实体表单或注册目录。

## 如何新增通用扩展

1. 根据类型创建目录，例如 `common/fields/` 或 `common/validators/`，组件从 [模板](../templates/README.md) 复制。
2. 新增 `manifests/common/<类型>/<名称>.v1.extension.json`；`implementation.path` 指向刚创建的文件。
3. 用 `configSchema` 声明变化参数，避免在代码中固定某个实体编码、字段名或业务额度。确有范围要求则声明 `supportedEntityCodes`。
4. 从 `workflow-web/` 执行 `npm run extensions:check`、`npm run test:extensions`、`npm run build`，然后在两个不同实体配置不同参数验证复用行为。

CLASS、OBJECT、FACTORY 的选择和清单示例见 [通用清单说明](../manifests/README.md)。

## 通用校验器

本节实现位于 `common/validators/`。

`AmountValidator.js` 导出 `AmountValidator` 类，清单安装名为 `amount`，版本为 1。其 `validate(value, context)` 检查非负有限数字及 `context.params.maxAmount` 上限；空值通过，必填由独立规则负责。

### 绑定到实体或流程表单

1. 在表单设计器选择金额字段，进入数据校验中的自定义校验配置。
2. 选择“金额校验” `amount@1`，例如设置 `maxAmount=1000`，选择 CHANGE/BLUR 输入反馈时机。
3. 保存、预览并发布；测试 1000 通过、1001 报错、负数报错。需要非空时另外启用必填。
4. 流程节点使用该实体表单时复用同一绑定；不会自动作用于实体的其他表单。

以下为 `field.validationRules` 中的配置片段，优先通过设计器生成：

```json
{
  "customValidators": {
    "version": 1,
    "rules": [{ "name": "amount", "version": 1, "params": { "maxAmount": 1000 }, "triggers": ["CHANGE", "BLUR"] }]
  }
}
```

SUBMIT 会执行全部已绑定规则，`triggers` 只控制输入时的反馈。当前清单限定了数值类型和 STRING；额度参数必须是非负有限数字。

### 在独立代码中调用

下面代码可放在项目 JS 模块中；别名 `@` 由 Vite 解析：

```js
import { AmountValidator } from '@/extensions/common/validators/AmountValidator.js'
import { validateCustomValue } from '@/extensions/contracts/validation.js'

const result = await validateCustomValue(new AmountValidator(), 1001, {
  params: { maxAmount: 1000 }
})
// result 为 { valid: false, message: '金额不能超过 1000' }
```

不要用 `if (validator.validate(...))` 判断成功，因为错误文本也是 truthy。Element Plus 页面可使用 `createElementPlusValidator`，完整示例见 [ValidatedAmountForm.vue](../examples/contracts/ValidatedAmountForm.vue)。

### 新增规则与验证

新增类、对象或同步工厂并实现 `validate(value, context)`，然后新增 VALIDATOR JSON；注册后还必须在具体字段绑定。示例见 [契约调用示例](../examples/README.md)。从 `workflow-web/` 执行 `npm run test:custom-validators`；改变交互时再执行 `npm run test:custom-validators:browser`。
