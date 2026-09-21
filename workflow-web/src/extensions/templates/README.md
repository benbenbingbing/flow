# 扩展开发模板

这些 Vue 文件可以复制到业务或通用模块，保留契约并替换展示与业务逻辑。模板本身不自动安装；复制完成后编写 JSON 清单。`manifests/examples/contracts/` 提供对应的默认禁用清单。

| 模板 | 契约与注意事项 |
| --- | --- |
| CustomFieldTemplate | 单字段值；保留 change/blur/focus，参数从 field.componentProps 读取 |
| CustomNodeTemplate | 整份字段对象；跨字段只读摘要，不能假定宿主收集节点 validate |
| CustomFormTemplate | 复用标准表单渲染、联动及 validate；自绘时显示字段错误并落实只读 |
| CustomListTemplate | 最小列表骨架；分页通过事件，动作交回 runtime |
| CustomCellTemplate | value 已格式化，业务原值从 row 取 |
| CustomButtonTemplate | 遵守 disabled/reason，异步错误自行处理 |
| CustomConditionTemplate | 仅编辑参数，实际判定需要后端支持相同类型 |
| RelatedContentTemplate | 通过 runtime.query/dispatch 访问关联数据，不套用普通列表 props |

参数和事件详细说明见 `../contracts/`，使用前阅读模板顶部注释。对象/数组默认值使用工厂，避免页面实例共享状态。
