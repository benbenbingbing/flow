# 项目业务扩展清单

此目录按 forms、fields、nodes、lists、list-cells、buttons、actions、validators、rules、permissions 分类声明扩展；只创建实际需要的类型目录。每个 `*.extension.json` 是一个注册身份和版本，自动发现，无需维护 index。

实现可以位于 extensions 对应模块或 src/modules 内；JSON 的 implementation.path 使用相对 workflow-web 的源码路径。
