# 测试问题记录

## 测试环境
- 运行日期: 2026-07-10
- 后端: Spring Boot 3.5.14, Java 17, Maven 3.9.15
- 前端: Vue.js (Vite)
- 数据库: MySQL (Flyway 迁移)
- 流程引擎: Flowable 7.2.0

---

## 一、已修复问题

### 1.1 EntityFormTimestampMappingTest (已修复)
**文件**: `EntityForm.java`, `EntityFormMapper.java`
```
- EntityForm.createTime/updateTime 缺少 @TableField 注解 → 已添加
- EntityFormMapper.selectByEntityId SQL 包含 ORDER BY create_time → 已移除
```

### 1.2 SchemaRequiredTablesTest (已修复)
**文件**: `SchemaRequiredTablesTest.java`
```
- 原测试检查不存在的种子文件 V002__seed_builtin_data.sql → 改为检查 V001 schema
```

### 1.3 PermissionSqlBuilderTest (已修复)
**文件**: `PermissionSqlBuilderTest.java`
```
- 测试期望错误的列名 created_by，实际系统使用 create_by → 修正测试断言
```

### 1.4 编译基础设施 (已修复)
**文件**: `EntityFlowStatusController.java`, `EntityFlowStatusService.java`
```
- 控制器 SaveStatusMappingRequest 内部类依赖 Lombok @Data → 改为显式 getter
- 服务类依赖 @Slf4j、@RequiredArgsConstructor → 改为显式 Logger 和构造器
```

---

## 二、遗留问题

### 2.1 Mockito 基础设施问题 (预置问题，161 个测试)
**影响**: 所有使用 @MockBean/@SpyBean 的测试 (ControllerTest/ServiceTest)
**原因**: Mockito 无法初始化 MockMaker 插件
**错误**: `Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)`
**建议**: 检查 Mockito 版本兼容性或添加 mockito-inline 依赖

### 2.2 EntityRuntimeRecordMapperTest (3 个测试)
**文件**: `EntityRuntimeRecordMapperTest.java`
**原因**: 使用了 Java 16+ 的 Type Patterns 特性，编译器配置异常
**状态**: Java 17 可用，但 Maven 编译时源级别可能低于 16

### 2.3 前端 calcEngine 计算引擎测试 (2/5 失败)
**文件**: `workflow-web/src/utils/__tests__/calcEngine.spec.js`
**失败用例**:
- `should support && logical expression`
- `should support contains()`
**原因**: 可能是 `&&` 运算符和 `contains()` 函数未完整实现

---

## 三、测试统计摘要

| 类别 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|------|------|------|------|------|------|
| 全部后端测试 | 202 | 36 | 1 | 161 | 1 |
| 非 Mockito 后端测试 | 29 | 26 | 0 | 3 | 0 |
| 前端 calcEngine 测试 | 5 | 3 | 2 | 0 | 0 |
