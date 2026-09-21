# 契约调用示例

AmountValidator.js 复用通用金额实现，并展示函数形式校验；ValidatedAmountForm.vue 展示页面调用。refreshAction.js 演示列表动作的异步错误处理，permissions.js 演示权限候选项。配置示例见 manifests/examples/contracts。

`validatorFactory.js` 演示同步实例工厂及参数读取，`nodeConfig.js` 演示不修改旧快照的配置升级。两种校验器清单分别展示 OBJECT / FACTORY，common/validators/amount 清单展示 CLASS。
