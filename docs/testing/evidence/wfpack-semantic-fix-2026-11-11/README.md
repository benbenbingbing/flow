# wfpack 语义校验修复证据

对应 [修复及回归报告](../../wfpack-semantic-validation-fix-2026-11-11.md)。

- [api-results.json](api-results.json)：42 个异常请求的 HTTP / 业务结果与无残留断言，4 个正常导入场景、来源确认、Flyway 及批次状态结果。不包含登录令牌。
- [api_regression.py](api_regression.py)：实际 API 重放脚本，需先准备隔离的 `semantic_target` MySQL schema、相应 socket、应用端口及仅存于临时目录的 `credentials.json`；通过 `--base` 和 `--fixtures` 指定路径。不得指向开发或生产数据库。
- [api-regression.log](api-regression.log)：最终实机回归摘要。
- [all-migration-test.log](all-migration-test.log)：最终完整迁移模块 317 项通过。
- [semantic-test-results.xml](semantic-test-results.xml)：新增语义回归 87 个参数化实例结果。
- [app-build.log](app-build.log)：应用完整打包成功，打包时使用 `-DskipTests`。
- [verification.json](verification.json)：源码与被测副本一致性、JAR SHA256、测试数、迁移数和状态核对。
- [cleanup.json](cleanup.json)：本次三个临时端口关闭、凭据和数据目录删除、原开发服务进程未变化。
- [current-full.wfpack](current-full.wfpack)、[current-partial.wfpack](current-partial.wfpack)：从本次应用实际导出的合成正常包；均已完成导入及发布。

原始七个反例仍位于 [上一轮复现包目录](../wfpack-negative-2026-11-11/fixtures)，副本纳入模块 `src/test/resources/wfpack-semantic`，资源 README 记录原文摘要。测试没有使用真实业务数据。
