# 平台必需清单

此目录按 forms、fields、nodes、lists、list-cells、buttons、actions、validators、rules、permissions 分类声明扩展；只创建实际需要的类型目录。每个 `*.extension.json` 是一个注册身份和版本，自动发现，无需维护 index。

平台字段别名、默认类型及兼容类型在 JSON 中统一声明；修改后运行 `npm run extensions:generate` 同步派生数据。
