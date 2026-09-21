# Demo 清单

此目录按 forms、fields、nodes、lists、list-cells、buttons、actions、validators、rules、permissions 分类声明扩展；只创建实际需要的类型目录。每个 `*.extension.json` 是一个注册身份和版本，自动发现，无需维护 index。

示例受 `enableDemo` 控制；contracts 下模板默认禁用，需要同时将 JSON 的 enabled 改为 true。复制到实际业务时移入 business/<module> 或 common 并使用独立稳定名称。
