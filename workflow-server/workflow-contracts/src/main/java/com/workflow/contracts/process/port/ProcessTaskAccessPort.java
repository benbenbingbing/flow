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

    /** 服务端从待办表和流程发布历史联合解析出的可信审批上下文。 */
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
