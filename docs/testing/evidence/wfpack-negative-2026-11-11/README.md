# wfpack 补充测试证据

对应 [测试报告](../../wfpack-negative-matrix-2026-11-11.md)。

- `matrix-final.json`：111 个独立用例最终响应和断言；7 个语义反例未达预期。
- `matrix-before-version-fix.json`：版本覆盖与回滚问题修复前结果。
- `matrix-before-subform-fix.json`：子表单失败包初次复现结果。
- `final-verification.json`：基点、最终主代码/JAR SHA256、三库迁移与状态检查。
- `fixtures/`：合成正常包与复现包；`fixture-sha256.json` 可核对内容。
- `scripts/`：本机隔离测试脚本。路径和 MySQL 安装位置需按实际环境调整；依赖重新生成的临时环境及凭据，不包含实际密码或签名密钥。`round10_subforms.py` 为最初建模脚本；`round10_resume.py` 记录原失败包重试和真实保存；最终升级/回滚由 round11 脚本覆盖。
- `all-migration-final-test.log`：迁移模块完整 230 个测试结果。
- `final-subform-update-build.log`：最终构建和 199 个定向测试。
- `final-*.log`、`round*-final.log`：多轮最终执行日志。脚本退出 0 不代表全部断言通过，以 JSON 中 passed 为准。
- `cleanup.json`：临时应用、MySQL 和凭据清理结果。

JSON 已去除登录、预览和影响确认令牌以及冗长快照副本；未保存真实用户数据或生产凭据。
