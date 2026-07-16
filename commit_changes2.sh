#!/bin/bash
set -e

cd /Users/dawei/Documents/ddup/ai/flow

# Commit 1: Pagination feature for process and entity lists
git add \
  workflow-server/src/main/java/com/workflow/controller/EntityDefinitionController.java \
  workflow-server/src/main/java/com/workflow/controller/ProcessDefinitionController.java \
  workflow-server/src/main/java/com/workflow/service/EntityDefinitionService.java \
  workflow-server/src/main/java/com/workflow/service/ProcessDefinitionService.java \
  workflow-server/src/main/java/com/workflow/dto/EntityDefinitionQueryDTO.java \
  workflow-server/src/main/java/com/workflow/dto/ProcessDefinitionQueryDTO.java \
  workflow-server/src/test/java/com/workflow/controller/EntityDefinitionControllerTest.java \
  workflow-server/src/test/java/com/workflow/controller/ProcessDefinitionControllerTest.java \
  workflow-web/src/api/entity.js \
  workflow-web/src/api/process.js \
  workflow-web/src/views/EntityList.vue \
  workflow-web/src/views/ProcessList.vue

git commit -m "feat: 流程管理与实体配置列表支持查询条件和分页

- ProcessDefinitionController / EntityDefinitionController 新增分页查询接口
- 新增 ProcessDefinitionQueryDTO / EntityDefinitionQueryDTO 查询条件
- Service 层使用 MyBatis Plus 分页支持关键字、状态、分类等过滤
- ProcessList / EntityList 页面新增查询表单与 el-pagination 分页组件"

# Commit 2: Startup fixes
git add \
  workflow-server/src/main/java/com/workflow/mapper/FlowActionExecutionMapper.java \
  workflow-server/src/main/java/com/workflow/mapper/FlowActionMapper.java \
  workflow-server/src/main/java/com/workflow/controller/ConfigMigrationController.java \
  workflow-server/src/main/java/com/workflow/service/migration/ConfigMigrationPackageService.java \
  workflow-server/src/main/java/com/workflow/service/migration/DownloadFile.java

git commit -m "fix: 修复 Text Blocks 与嵌套 record 导致的启动失败

- 将 FlowActionExecutionMapper / FlowActionMapper 中的 Java Text Blocks 改为字符串拼接，
  避免部分编译环境下 source level 不兼容
- 将 ConfigMigrationPackageService 中的嵌套 record DownloadFile 提取为顶层类，
  解决 Spring 容器启动时 NoClassDefFoundError"

# Commit 3: All remaining user changes
git add -A

git commit -m "feat: 配置迁移与动态扩展演示相关更新

- 新增配置迁移资产、导出/导入包、环境映射等实体与服务
- 新增 ConfigMigrationController 与前端 ConfigMigration 页面
- 新增动态扩展演示脚本与文档
- 补充实体表单/列表配置校验与测试"

echo "All commits created successfully."
