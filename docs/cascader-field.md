# 级联选择字段

级联选择适用于省市区、分类、组织树等分层数据。PC 与移动端使用相同配置和接口协议，保存值始终为一条从根到叶子的完整编码路径，例如 `["guangdong", "guangzhou", "tianhe"]`。清空时保存 `[]`。不支持多选、仅保存末级编码或选择非叶子节点。

## 设计器配置

在表单字段的组件类型中选择“级联选择”，在组件参数配置数据来源和加载方式；选择接口来源后，在该字段的“数据源绑定 → 字段选项（FIELD_OPTIONS）”绑定一条只读接口。保存并发布表单后生效。

| 组件参数 | 值 | 用途 |
| --- | --- | --- |
| `cascaderSource` | `STATIC`（默认）、`INTERFACE` | 静态树或已注册接口 |
| `cascaderOptions` | JSON 数组 | 静态来源的完整树；未填写时为空，不显示演示数据 |
| `cascaderLoadMode` | `ALL`（默认）、`CHILDREN` | 接口返回完整树，或每次只返回指定父节点的直接孩子；静态来源始终按完整树读取 |

组件参数示例：

```json
{
  "cascaderSource": "INTERFACE",
  "cascaderLoadMode": "CHILDREN"
}
```

字段接口绑定示例（`extensionId` 使用扩展管理中已经注册的接口 ID）：

```json
{
  "FIELD_OPTIONS": {
    "extensionId": "region-options",
    "enabled": true
  }
}
```

接口要求：类型 `INTERFACE`、读写类型 `READ`、调用上下文 `FORM`、输出 Schema 的根类型 `array`，作用域覆盖当前表单。接口模式必须且只能有一条启用的 `FIELD_OPTIONS` 绑定。当前模式没有可用绑定时组件展示加载错误，不能用空选项掩盖配置问题。数据接口由已有受控 Provider 实现，组件参数不接收任意 HTTP URL。

## 调用入口及权限

复用 `POST /api/ui-runtime/extensions/execute`。PC、移动端均携带当前表单的 `configHash` 与实际 `runtimeContext`；服务端继续校验发布绑定、实体/记录权限、流程任务及只读来源。父路径是业务输入，不能替代数据权限；Provider 查询时仍须限制可见范围，不能因为客户端传入某个父编码便返回无权查看的数据。

全量请求示例：

```json
{
  "ownerType": "FORM",
  "ownerId": "form-id",
  "configHash": "current-form-config-hash",
  "runtimeContext": {},
  "bindingCode": "FIELD_OPTIONS",
  "targetType": "FIELD",
  "targetKey": "region",
  "extensionId": "region-options",
  "input": {
    "mode": "edit",
    "fieldCode": "region",
    "formData": {},
    "cascader": {
      "loadMode": "ALL",
      "parentValue": null,
      "path": [],
      "level": 0
    }
  }
}
```

`input.cascader` 是组件生成的协议参数，即使配置了输入映射也会保留，不能被映射或固定输入覆盖。其他业务条件可通过既有输入映射传入。

| `input.cascader` 字段 | 含义 |
| --- | --- |
| `loadMode` | `ALL` 或 `CHILDREN` |
| `parentValue` | 父节点的原始编码；根请求为 `null`，数字 `0` 是有效编码 |
| `path` | 从根到父节点的完整编码路径；根请求为 `[]`，不包含待返回的孩子 |
| `level` | 等于 `path.length`；根请求为 `0` |

按级加载根节点时，只将以上 `loadMode` 改成 `CHILDREN`。展开广东省时，组件发起同一绑定的请求，`input.cascader` 为：

```json
{
  "loadMode": "CHILDREN",
  "parentValue": "guangdong",
  "path": ["guangdong"],
  "level": 1
}
```

展开广州市时，路径为 `["guangdong", "guangzhou"]`、父编码为 `"guangzhou"`、层级为 `2`。不同分支可以使用相同编码，Provider 应结合完整 `path` 定位父节点，不能仅依赖 `parentValue`。

## 响应节点规范

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `value` | 非空字符串或有限数字 | 必填，同一级唯一；保持类型，`1` 与 `"1"` 不相等；避免超出 JavaScript 安全整数范围的数字 ID，长 ID 用字符串 |
| `label` | 非空字符串 | 必填，界面显示名称 |
| `disabled` | 布尔值 | 可选，默认 `false`，该节点及其路径不可选择 |
| `children` | 节点数组 | 只用于 `ALL`/静态树；省略或 `[]` 表示叶子，不允许 `null` |
| `leaf` | 布尔值 | `CHILDREN` 必填；`ALL` 可省略，若提供必须与 `children` 是否非空一致 |

完整树最多 32 层、10000 个节点。按级模式采用相同层数与已加载节点总数限制；数据量大时优先使用按级模式。接口返回结构不符合规范时，组件展示错误并允许重试，不自动清除表单中已有选择。

### 全量加载

Provider 直接返回完整树数组，不要自行包裹 `Result`、`data` 或 `options`：

```json
[
  {
    "value": "guangdong",
    "label": "广东省",
    "children": [
      {
        "value": "guangzhou",
        "label": "广州市",
        "children": [
          { "value": "tianhe", "label": "天河区" },
          { "value": "yuexiu", "label": "越秀区", "disabled": true }
        ]
      }
    ]
  }
]
```

控制器统一包装后的 HTTP 成功响应为：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "value": "guangdong",
      "label": "广东省",
      "children": [
        { "value": "guangzhou", "label": "广州市" }
      ]
    }
  ]
}
```

选项接口保持原始嵌套对象，现有接口执行与 Schema 校验不裁掉 `children`、`leaf`、`disabled`。

### 按级加载

根请求返回一级节点：

```json
[
  { "value": "guangdong", "label": "广东省", "leaf": false },
  { "value": "zhejiang", "label": "浙江省", "leaf": false }
]
```

广东省子请求只返回直接孩子：

```json
[
  { "value": "guangzhou", "label": "广州市", "leaf": false },
  { "value": "shenzhen", "label": "深圳市", "leaf": false }
]
```

广州市子请求返回叶子节点：

```json
[
  { "value": "tianhe", "label": "天河区", "leaf": true },
  { "value": "yuexiu", "label": "越秀区", "leaf": true, "disabled": true }
]
```

按级响应禁止 `children` 字段（包括 `children: []`）。`leaf: false` 表示还可以继续展开，返回 `[]` 时不能把该父节点转成可选择叶子；请保证 `leaf` 与实际数据一致。失败允许重新加载，不能把失败结果当作空成功结果缓存。

## 输出 Schema 配置示例

以下可填入接口扩展的输出 Schema，适合字符串编码的按级接口：

```json
{
  "type": "array",
  "items": {
    "type": "object",
    "required": ["value", "label", "leaf"],
    "properties": {
      "value": { "type": "string" },
      "label": { "type": "string" },
      "leaf": { "type": "boolean" },
      "disabled": { "type": "boolean" }
    }
  }
}
```

全量接口将 `required` 改为 `["value", "label"]`，并在 `properties` 中加入 `"children": { "type": "array" }`。数字编码接口把 `value.type` 改为 `number`。该示例用于后端现有 Schema 子集校验，客户端仍递归核验完整树、同级唯一性、叶子一致性和数量限制。

## 已有值回显与异常处理

- 全量模式加载一次树后，按完整路径匹配并显示名称。
- 按级模式按已保存路径逐级请求，完成编辑、审批和只读场景的回显，不要求另设“按编码查询名称”接口。
- 已存路径不存在、包含禁用节点或接口异常时，保留原值并显示提示。切换分支、清空或重新选择由用户明确触发。
- 同字段请求按父路径区分，快速切换时旧响应不能覆盖新状态；数据接口、表单记录或映射依赖变化后重新加载。

此次功能使用现有组件参数和数据源绑定字段，无数据库结构变更、无新增或修改 Flyway 迁移。
