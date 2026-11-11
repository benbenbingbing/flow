# 流程及实体配置跨环境引用修复

日期：2026-11-11

## 修复范围

源环境和目标环境的目录 ID 可以不同，迁移使用用户登录名及角色、用户组、部门编码建立关联。目标端不能因源 ID 恰好存在就复用该对象。

| 问题 | 本次处理 | 阻断时的处理办法 |
| --- | --- | --- |
| 实体字段、表单字段、节点属性、列表查询的固定默认值保留源 ID | 根据实体字段类型，将 USER/DEPT 及 REFERENCE/MULTI_REFERENCE 指向的 USER/ROLE/GROUP/DEPT 转为带类型的编码引用，导入恢复目标 ID；保持标量、数组及 JSON 字符串表示 | 补齐目标同编码目录或配置编码映射；裸 ID 旧包需更新源系统后重新发布、导出 |
| FIELD/CONDITION 的固定比较值改变规则结果 | 字段契约覆盖默认值及条件常量；系统字段 submitterId、deptId、create_by、currentTaskAssignee 等采用实际运行时 ID 语义；细粒度 UI 包携带字段类型元数据 | 缺少字段契约时重新导出；普通文本不转换；引用字段的范围、模糊比较需改成等值、集合或稳定业务编码比较 |
| 不透明 SQL、表达式、脚本、扩展参数夹带固定目录 ID | 源端检查代码字面量和参数中的现存目录 ID；代码不执行、不自动替换；扩展支持显式 JSON Pointer 引用声明 | SQL 使用对应运行时支持的参数或按稳定编码查目录；表达式改用运行时参数；扩展声明引用类型，或传稳定编码并自行解析 |
| BPMN 使用全文替换导致节点、脚本或说明意外变化 | 安全解析 XML，只转换实体表单属性、扩展元素及历史 userTask formKey；支持单值、逗号列表、JSON 数组、不同命名空间前缀；正文全部表单纳入依赖 | 未转换的裸表单 ID 或缺失源表单需在源设计器重新选择实体表单，发布流程后导出 |
| 自定义业务记录的固定引用无法根据记录 ID 跨环境定位 | 已明确为业务记录引用的静态默认值、筛选或规则常量阻断 | 改用稳定业务编码比较，或配置按业务编码查询的动态默认值、筛选 |

默认值、规则常量及参数的身份依赖参与既有依赖清单、映射、分析和发布前复查。分析不依赖清单是否完整，正文中的身份引用也会重新解析。声明过的扩展参数可在目标端直接根据包内类型解析，无需安装源端声明适配器。

## 阻断提示与操作

引用问题的分析报告提供 `path`、`message` 和 `suggestion`。API 异常文本也包含配置位置、原因和建议。

导入分析阻断后，页面切换到“影响对比”，自动展开阻断资产，展示每项问题的位置及建议；目录依赖缺失、结构风险和配置冲突同样提供处理办法。

常用处理路径：

1. **目标缺少目录对象**：在目标建立同登录名/编码对象，或在“环境依赖映射”填写实际目标编码，再重新分析。映射值应为目标编码，不是目标 ID。
2. **旧包缺少稳定引用或字段契约**：升级源端，重新发布受影响配置并导出。不要手工编辑归档文件或把源 ID 填入映射。
3. **SQL 固定用户/部门**：权限 SQL 可用 `#{userId}`、`#{deptId}`、`#{orgId}`；固定对象按 `username`、`role_code`、`group_code`、`org_code` 查询其目标 ID。其他执行器应使用自身支持的参数协议。
4. **BPMN 表达式或脚本固定 ID**：把 ID 移入有明确引用契约的扩展参数，或通过按稳定编码查询的解析器获取；表达式引用运行时参数。
5. **扩展参数未声明引用**：实现下述 SPI，在提示的参数路径声明 USER/ROLE/GROUP/DEPT。确认是普通业务值时显式声明 VALUE；不要把身份引用标为 VALUE 来绕过检查。
6. **自定义记录默认值**：取消静态记录 ID，改用业务编码查询的动态默认值；规则可直接比较业务编码字段。

## 扩展参数声明示例

扩展模块实现 `com.workflow.contracts.migration.spi.ConfigMigrationParameterReferenceProvider` 并注册为 Spring Bean。适配器只说明类型，平台负责目录查询、依赖生成和转换：

```java
@Component
public class NotifyMigrationReferences implements ConfigMigrationParameterReferenceProvider {
    @Override
    public Map<String, String> referenceTypes(Map<String, Object> configuration, String parameterName) {
        if (!"NOTIFY".equals(configuration.get("actionCode")) || !"paramsJson".equals(parameterName)) {
            return Map.of();
        }
        // recipients 是 ID 数组；businessCode 按扩展运行时契约作为稳定业务编码传递。
        return Map.of("/recipients", "USER", "/businessCode", "VALUE");
    }
}
```

适用于 `paramsJson`、`extraParams`、`implementationConfigDocument`。JSON Pointer 相对参数文档，空路径表示整个值，数组位置使用具体下标；父子重叠路径、缺失路径、未知类型会阻断并提示修正。声明存放于导出副本 `_migrationParameterReferences`，导入物化时移除，不写入源业务配置。

## 版本及边界

- 发布包格式升级为 **4**，快照 schema 升级为 **3**，引用协议 `_referenceProtocol=1`。旧导入器会拒绝新包，避免误把编码引用当成运行时 ID；应先升级目标端再迁移新包。
- 格式 3 仍可读取；裸身份默认值、缺少字段契约的条件及未经过源端检查的代码/非空扩展参数会阻断，给出重新导出建议。
- 字段类型元数据 `_referenceFields` 只说明引用语义，系统实体 UI 包不因此包含系统表结构。细粒度选择会保留元数据。
- 历史资产及其内容哈希不回写；导出工作副本进行升级。
- SQL/脚本检查不是完整语言解析器，只识别已知代码位置中的字面量与源目录现存 ID；不会执行代码。计算、拼接、编码后的 ID，已删除的源目录 ID，以及未声明类型的自定义业务参数不能仅凭文本可靠推断。扩展应提供契约或使用稳定编码，不应据“未检出”认定任意脚本完全可移植。
- 本次不迁移用户、角色、组织等目录数据，也未连接或修改实际业务数据库。

## 验证

后端在 `/tmp/flow-config-reference-build` 使用当前源码独立构建，避免 IDE 自动编译覆盖 Maven 产物：

```sh
mvn -o -pl workflow-migration -am test \
  -Dtest='ConfigMigration*Test,AttachmentItemMigrationSupportTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false
```

回归覆盖两套不同目录 ID、源 ID 撞号、普通文本保留、默认值各入口、规则求值一致、集合条件、部门/角色引用、细粒度包、系统字段、空值筛选、未知类型及自定义记录阻断、参数声明及移除、旧包阻断、SQL/BPMN 提示、额外表单绑定、声明位置替换及 DTD 拒绝、报告持久化和发布前正文复查。

实际结果：**191 项测试通过，0 失败，0 错误，0 跳过**；其中本次新增 20 项回归用例。日志位于 `/tmp/flow-config-reference-isolated-tests.log`。

前端执行 `npm run test:config-migration` 和 `npm run build`，验证报告建议提取以及管理端、嵌入端编译。未执行浏览器交互验收或七种数据库集成测试；本次不涉及数据库结构。

Flyway 迁移文件：新增 **无**，修改 **无**，删除 **无**。本次未修改 SQL/Java 版本迁移，因此不需要同步七种空库初始化脚本。
