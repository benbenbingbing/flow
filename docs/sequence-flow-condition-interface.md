# 顺序流业务条件接口

顺序流的“流转条件 → 条件类型”新增“业务接口”。设计者选择已注册的 Java 条件接口，
填写 JSON 对象参数后应用到画布、保存并发布。接口返回 `true` 表示该条件命中，返回
`false` 表示不走该分支。接口异常阻止本次办理并回滚，不等同于 `false`。

## 注册与使用

业务模块实现 `SequenceFlowConditionProvider` 并注册为 Spring Bean。稳定编码
`getCode()` 用于流程绑定，`getDisplayName()` 用于下拉显示；编码不区分大小写且必须唯一。
注册后自动出现在扩展目录的“流程条件接口”和连线选择器中，不需要新增数据库字段或目录数据。
这些接口由代码注册，目录只提供查看；名称、说明和实现的调整通过业务模块发布。
接口类须在应用组件扫描范围内，业务模块部署并重启后端服务后，可按 `getCode()` 编码或
`getDisplayName()` 名称搜索。“条件接口”旁的问号提供接口实现和注册说明；“接口参数”旁的
问号提供 JSON 示例、代码读取方式及自动传入的上下文说明。

```java
@Component
public class ExpenseRouteCondition implements SequenceFlowConditionProvider {
    @Override public String getCode() { return "expenseRoute"; }
    @Override public String getDisplayName() { return "费用审批分支判断"; }

    @Override
    public boolean evaluate(SequenceFlowConditionRequest request) {
        // 示例：真实业务可用 processInstanceId 查询各节点本轮审批记录后汇总判断。
        // variables 包含本次提交的 action 和表单变量，extraParams 是连线的业务参数。
        return "approve".equals(request.variables().get("action"));
    }
}
```

接口请求提供流程定义、流程实例、执行路径、连线及起止节点、实体编码和记录 ID，
以及不可修改的变量与参数快照。平台内部安全变量不会传给业务接口。
参数合法性可以在 `validate(extraParams)` 中校验，发布和实际调用都会检查。
实现只允许查询和判断，不应写业务数据、发送消息或推进流程；引擎重试可能重复调用。

## 网关行为

- 排他网关：按模型顺序选择第一个命中的分支。需要唯一业务结果时应让接口规则互斥。
- 包容网关：执行所有命中的分支。
- 默认流：仅在其他分支均未命中时执行，不配置条件接口。
- 并行网关：不判断条件，禁止配置业务接口；请在并行汇聚后增加排他或包容网关。

例如：财务、法务并行办理后汇聚，接一个排他网关；“继续审批”连线选择业务条件接口，
由该接口读取本实例各节点的审批记录并返回是否通过，另一条连线设为默认流。
不必为每个节点或分流额外创建业务字段。

## 保存、发布和事务

配置保存在顺序流的 `sequenceFlowCondition` BPMN 扩展属性：

```json
{"providerCode":"expenseRoute","extraParams":{"nodeIds":["Task_Finance","Task_Legal"]}}
```

业务参数由接口定义，不由平台推断。发布器检查接口可用性和参数，再编译为平台固定的
条件入口；不支持在普通表达式中任意调用 Java Bean 或方法。运行时始终读取已部署版本的
连线配置，后续草稿修改不会影响运行中的实例。

界面的“接口参数”只填写上例中的参数对象本身：

```json
{"nodeIds":["Task_Finance","Task_Legal"],"expectedAction":"approve"}
```

接口可用 `request.extraParams().get("nodeIds")` 读取列表、用
`request.extraParams().get("expectedAction")` 读取期望动作。以上键名均为业务示例，
只有接口实现主动读取后才生效，平台不会自动汇总审批记录。
`nodeIds` 填写节点 ID，可在节点的“常用 → 基本配置”中查看。
没有额外参数时填写 `{}`，不需要再包一层 `extraParams`。
流程实例等上下文由平台传入；参数不执行 `${变量名}` 替换，动态变量由接口通过
`request.variables().get("变量名")` 读取。

普通审批、驳回和自定义审批完成时，先在同一事务中保存 `process_task` 的本次处理结果，
再调用引擎推进。因此条件接口通过同一数据源、同一事务查询时可以读到最后一笔提交；
条件异常时审批记录与引擎状态一起回滚。接口自行查询时仍需正确区分转办、会签汇总、
退回重审轮次；不能把历史上任意一次通过视为本轮通过，也不能使用独立连接查询未提交记录。

“下一审批人预览”不会提前执行业务接口；遇到该条件时返回 `DEFERRED`，提示提交后才能
确定路由，避免预览重复调用或读到提交前状态。该场景无法提前指定依赖接口判断的下一节点办理人。

## 验证

- 前端参数测试：`node --test src/components/__tests__/sequenceFlowCondition.spec.js`。
- 后端真实引擎测试：`SequenceFlowConditionFlowableTest`，覆盖 true/false、排他与包容、
  默认流、异常回滚、接口缺失/重复、参数和发布执行边界。
- 审批事务回归：`TaskCandidateApprovalTransactionIntegrationTest`，验证选路前本次审批
  记录可读、提交后外部连接可见，以及引擎完成后失败时完整回滚。

本功能不涉及 SQL 或 Java 数据库迁移，七种数据库初始化脚本的结构和种子数据无需调整。

2026-11-16 实际验证结果：前端参数测试 2 项、前端生产构建通过；功能及第一组回归
35 项通过，发布预检和下一审批人配置回归 50 项通过；第二组应用回归/架构检查
53 项中 52 项通过。唯一失败是既有 `ArchitectureBoundaryTest.HTTP_APIS_ONLY_USE_GET_OR_POST`：
未改动的 `PositionAssignmentController.remove/setMembers` 不符合现有 HTTP 方法规则。
相关控制器及规则与当前 HEAD 一致，本任务未调整该无关接口。
引擎与审批事务使用 H2 验证，未执行真实七种数据库联调或浏览器操作验收。
