# 自动生成字段策略

field-definitions.js 从 manifests/platform/fields 生成，为纯 JS 字段规则与测试提供无 Vue 的元数据。禁止手改；修改源 JSON 后执行 npm run extensions:generate，CI 用 extensions:check 检查一致性。
