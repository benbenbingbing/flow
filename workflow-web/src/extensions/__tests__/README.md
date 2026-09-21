# 扩展测试

- registration.spec.mjs：正式清单发现、格式/路径/冲突、函数与类、幂等、Demo、字段解析和金额参数。
- catalog-and-contract.spec.mjs：十类目录投影、纳管边界、页面与关联宿主过滤、对象/工厂/钩子、Schema 路径和旧入口删除。
- build.spec.mjs：真实 Vite 构建拒绝不存在的具名导出，修正 JSON 后通过；校验/迁移示例的边界。
- helpers/：Node 测试从正式清单加载校验器，不参与产品注册。

运行 `npm run test:extensions`。`npm run test:extension-contracts` 编译全部契约、模板与示例。`npm run test:extensions:browser` 使用主应用真实入口、独立无头 Chrome 和临时组件/JSON 验证新增、修改、删除，退出时清理临时文件；可设置 CHROME_PATH。现有 `test:custom-validators:browser` 覆盖实体表单、子表、审批、只读和错误展示。
