# 前端扩展契约

这里定义组件 props、emits、动作上下文和校验协议。导入契约不会注册组件、发请求或启动应用。组件通过 defineProps/defineEmits 复用定义，业务实现通过 ../manifests 中的 JSON 安装。

| 文件 | 用途及关键约束 |
| --- | --- |
| form-field.js | 单字段 modelValue；参数从 field.componentProps 读取，保留 change/blur |
| form-node.js | 整份业务字段对象；局部更新保留其他字段，节点 validate 不会自动收集 |
| form.js | 整表单字段对象、模式、联动、唯一及自定义校验、受控动作插槽 |
| list.js | 整列表数据与 runtime；分页使用 sizeChange/pageChange 事件 |
| list-cell.js | 已格式化单元格 value、原始 row、渲染 config |
| list-action.js | 列表按钮函数和组件；选择结果与原宿主行分开 |
| action-rule.js | 条件参数编辑和权限选项；判定和授权仍在后端 |
| related-content.js | 关联内容的 query/dispatch/refresh，不等同普通页面 props |
| validation.js | validate(value, context) 的同步/异步返回值归一化和 Element Plus 适配 |
| data-source.js | 已发布接口运行调用及受控上下文 |
| subform.js | 子表输入参数与字段初始化映射 |
| component.js | 元数据和可配置参数属性说明 |
| runtime.js | 复用现有 Vue 和字段运行能力，导入会加载平台依赖 |
| index.js | 无 Vue 的纯契约导出 |

## 编写实现

组件模板位于 ../templates/。实现必须遵守宿主传入的 readonly/disabled、权限结果和模型更新协议；不要原地修改 props 中的配置或共享默认对象。

整表单自有校验可以暴露无参 validate()，返回 false 阻止提交。前端校验器则实现 validate(value, context)，返回 true/false、错误文本或 {valid,message}，支持 Promise；两种返回契约不能混用。前端自定义规则由宿主统一执行，不等于服务端强制约束。

## 注册与版本

实现不写 register*。为其添加 ../manifests 下的 JSON，声明 type、name、version、implementation 及 metadata。完整流程、类型清单和字段示例见 ../README.md。CLASS 使用无参构造，需依赖时使用同步 FACTORY；动态函数写在 JS 中并通过 path/export 引用。

规则绑定属于当前表单字段，不会自动影响实体其他表单。校验器 name/version 固定选择实现，变更业务规则、参数或适用范围应新增版本并保留旧实现。FORM/LIST 的多版本能力与其他注册表存在差异，参照扩展中心版本说明。

## 验证

从 workflow-web 运行 npm run test:extensions、npm run test:custom-validators、npm run test:extension-contracts 和 npm run build。模板编译测试会实际解析全部 Vue/JS 模块，浏览器测试覆盖字段、子表与自绘表单校验。
