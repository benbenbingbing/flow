package com.workflow.contracts.process.port;

import java.util.List;
import java.util.Optional;

/**
 * 业务记录的当前任务访问查询契约，不暴露流程引擎或持久化实现。
 *
 * <p>实体坐标和流程实例坐标同时提供时必须联合匹配；至少提供完整实体坐标
 * 或流程实例 ID。查询只用于计算入口能力，提交时仍须重新验证任务归属。</p>
 */
public interface ProcessTaskAccessPort {

    /**
     * 批量读取一页记录的入口能力，只用于本次展示；真正提交时仍调用精确任务校验。
     * 平台实现按批查询，默认实现供既有外部适配器保持兼容，不能把候选人当作实际办理人。
     */
    default java.util.Map<RecordCoordinates, TaskCapability> findCapabilities(
            String userId, List<RecordCoordinates> records) {
        var result = new java.util.LinkedHashMap<RecordCoordinates, TaskCapability>();
        for (RecordCoordinates record : records) {
            String taskId = findActionableTaskId(userId, record.entityCode(), record.entityDataId(),
                    record.processInstanceId()).orElse(null);
            String taskName = taskId == null ? null : findActionableTaskName(userId, taskId,
                    record.entityCode(), record.entityDataId(), record.processInstanceId()).orElse(null);
            result.put(record, new TaskCapability(taskId, taskName, isCurrentAssignee(userId,
                    record.entityCode(), record.entityDataId(), record.processInstanceId())));
        }
        return java.util.Map.copyOf(result);
    }

    /** 完整实体坐标或流程坐标至少具备一组；两组存在时必须联合匹配。 */
    record RecordCoordinates(String entityCode, String entityDataId, String processInstanceId) {}

    /** 同一用户/记录下最新可办理任务及实际办理人身份；候选审批能力与办理人身份分别保存。 */
    record TaskCapability(String taskId, String taskName, boolean currentAssignee) {}

    /**
     * 查找该记录上当前用户可审批的任务，包含已指派任务和未认领的真实候选任务。
     *
     * @param userId 用户 ID 或用户名
     * @param entityCode 实体编码，与 entityDataId 一起提供
     * @param entityDataId 实体记录 ID，与 entityCode 一起提供
     * @param processInstanceId 流程实例 ID，可单独作为记录坐标
     * @return 当前用户可审批的任务 ID；用户或记录坐标无效时为空
     */
    Optional<String> findActionableTaskId(
            String userId, String entityCode, String entityDataId, String processInstanceId);

    /**
     * 读取已确定可办理任务的真实节点名称，供列表将可见审批目标与提交 taskId 对齐。
     * 实现必须复用用户、任务和记录的联合访问范围；旧适配器默认不返回名称。
     *
     * @param userId 当前认证用户
     * @param taskId 已由可办理能力选出的任务 ID
     * @param entityCode 业务实体编码
     * @param entityDataId 业务记录 ID
     * @param processInstanceId 流程实例 ID
     * @return 该任务的节点名称；任务不可访问或名称缺失时为空
     */
    default Optional<String> findActionableTaskName(
            String userId,
            String taskId,
            String entityCode,
            String entityDataId,
            String processInstanceId) {
        return Optional.empty();
    }

    /**
     * 按客户端声明的任务坐标回查当前用户真正可办理的服务端任务上下文。
     *
     * <p>该入口供审批表单按钮把短期发布令牌重新绑定到实际待办；实现必须同时
     * 校验用户、任务、记录和流程实例，不能仅凭客户端 taskId 返回上下文。</p>
     *
     * @param userId 当前认证用户 ID 或用户名
     * @param taskId 客户端声明、但尚未受信的任务 ID
     * @param entityCode 已鉴权业务记录的实体编码
     * @param entityDataId 已鉴权业务记录 ID
     * @param processInstanceId 已鉴权业务记录上的流程实例 ID
     * @return 完整、可办理且能定位不可变流程发布版的任务上下文
     */
    default Optional<ActionableTaskContext> findActionableTaskContext(
            String userId,
            String taskId,
            String entityCode,
            String entityDataId,
            String processInstanceId) {
        // 旧适配器没有精确任务上下文能力时必须安全拒绝审批按钮，不能降级成宽松的 taskId 查询。
        return Optional.empty();
    }

    /**
     * 判断当前用户是否实际持有该记录的任务；候选关系不授予此办理人身份。
     *
     * @param userId 用户 ID 或用户名
     * @param entityCode 实体编码，与 entityDataId 一起提供
     * @param entityDataId 实体记录 ID，与 entityCode 一起提供
     * @param processInstanceId 流程实例 ID，可单独作为记录坐标
     * @return 引擎存在实际指派给当前用户的未完成任务时为 true
     */
    boolean isCurrentAssignee(
            String userId, String entityCode, String entityDataId, String processInstanceId);

    /**
     * 查询指定实体中当前用户存在真实可审批任务的记录 ID，供 HAS_TODO 数据范围使用。
     *
     * @param userId 用户 ID 或用户名
     * @param entityCode 实体编码，必须提供，不能扩展为跨实体任务查询
     * @return 去重记录 ID；无候选任务或参数无效时返回空集合，不返回 SQL 或表名
     */
    List<String> findActionableEntityDataIds(String userId, String entityCode);

    /**
     * 服务端从待办表和流程发布历史联合解析出的可信审批上下文。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processVersionHistoryId 流程版本历史ID，后续用于处理可执行任务上下文时定位或关联目标
     * @param nodeId 节点ID，后续用于处理可执行任务上下文时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    record ActionableTaskContext(
            String taskId,
            String processInstanceId,
            String processDefinitionId,
            String processVersionHistoryId,
            String nodeId,
            String entityCode,
            String entityDataId) {
    }
}
