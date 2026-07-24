package com.workflow.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.AssigneeConfig.AssigneeType;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysRole;
import com.workflow.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 多实例集合变量自动准备监听器
 * 
 * 支持两种工作模式：
 * 1. 流程启动时预计算（主要方式）：prepareVariables() 在 startProcessInstance 前调用
 * 2. 运行时兜底（次要方式）：监听 ACTIVITY_STARTED 事件，处理子流程等动态场景
 */
@Slf4j
@Component
public class MultiInstanceCollectionListener implements FlowableEventListener {

    @Autowired
    private RuntimeService runtimeService;

    /** 流程定义配置 Mapper，查询流程配置 */
    @Autowired
    private ProcessDefinitionConfigMapper processMapper;

    /** 节点配置 Mapper，查询多实例节点 */
    @Autowired
    private NodeConfigMapper nodeConfigMapper;

    /** 审批人配置 Mapper，读取节点执行人配置 */
    @Autowired
    private AssigneeConfigMapper assigneeConfigMapper;

    /** 用户组 Mapper，按组码查询成员 */
    @Autowired
    private SysGroupMapper groupMapper;

    /** 用户-用户组关联 Mapper，查询组成员 */
    @Autowired
    private SysUserGroupMapper userGroupMapper;

    /** 角色 Mapper，按角色编码查询 */
    @Autowired
    private SysRoleMapper roleMapper;

    /** 用户-角色关联 Mapper，查询角色成员 */
    @Autowired
    private SysUserRoleMapper userRoleMapper;

    /** JSON 序列化工具 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 流程启动前预计算多实例集合变量（主要入口）
     */
    public void prepareVariables(String processConfigId, Map<String, Object> variables) {
        List<NodeConfig> nodes = nodeConfigMapper.findByProcessConfigId(processConfigId);
        for (NodeConfig node : nodes) {
            if (node.getConfigJson() == null) continue;
            try {
                Map<String, Object> config = objectMapper.readValue(node.getConfigJson(), Map.class);
                if (!Boolean.TRUE.equals(config.get("multiInstance"))) continue;

                String collectionExpr = (String) config.get("collection");
                if (collectionExpr == null || !collectionExpr.startsWith("${") || !collectionExpr.endsWith("}")) continue;

                String varName = collectionExpr.substring(2, collectionExpr.length() - 1);

                // 如果调用方已经传了这个变量，不覆盖
                if (variables.containsKey(varName)) continue;

                List<String> userIds = resolveAssignees(node.getId());
                if (!userIds.isEmpty()) {
                    variables.put(varName, userIds);
                    log.info("多实例变量预计算: nodeId={}, varName={}, users={}", node.getNodeId(), varName, userIds);
                }
            } catch (Exception e) {
                log.error("多实例变量预计算失败: nodeId={}", node.getNodeId(), e);
            }
        }
    }

    /**
     * 根据节点执行人配置解析出多实例用户ID列表。
     * <p>
     * 支持指定用户与角色/组类型；DEPT、LEADER、EXPRESSION 类型暂不自动处理。
     * 结果去重保持顺序。
     *
     * @param nodeConfigId 节点配置ID
     * @return 用户ID列表
     */
    private List<String> resolveAssignees(String nodeConfigId) {
        List<AssigneeConfig> assignees = assigneeConfigMapper.findByNodeConfigId(nodeConfigId);
        List<String> userIds = new ArrayList<>();

        for (AssigneeConfig assignee : assignees) {
            if (assignee.getAssigneeType() == null || assignee.getAssigneeValue() == null) continue;

            AssigneeType type = assignee.getAssigneeType();
            if (type == AssigneeType.USER) {
                userIds.add(assignee.getAssigneeValue());
            } else if (type == AssigneeType.ROLE) {
                // candidateGroups 在后端被保存为 ROLE 类型，可能对应用户组或角色
                // 角色编码可能带 ROLE_ 前缀（来自前端角色选择）
                String value = assignee.getAssigneeValue();
                String roleCode = value;
                if (value != null && value.startsWith("ROLE_")) {
                    roleCode = value.substring(5);
                }
                SysGroup group = groupMapper.selectByGroupCode(value);
                if (group != null) {
                    List<String> ids = userGroupMapper.selectUserIdsByGroupId(group.getId());
                    if (ids != null) userIds.addAll(ids);
                } else {
                    List<SysRole> roles = roleMapper.selectList(
                            new QueryWrapper<SysRole>()
                                    .eq("role_code", roleCode)
                                    .eq("deleted", 0)
                    );
                    if (roles != null && !roles.isEmpty()) {
                        List<String> ids = userRoleMapper.selectUserIdsByRoleId(roles.get(0).getId());
                        if (ids != null) userIds.addAll(ids);
                    }
                }
            }
            // DEPT、LEADER、EXPRESSION 等类型暂不自动处理
        }

        return new ArrayList<>(new LinkedHashSet<>(userIds));
    }

    /* ==================== 运行时兜底：全局事件监听 ==================== */

    /**
     * 监听活动开始事件，作为运行时兜底补充多实例集合变量。
     *
     * @param event Flowable 事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableActivityEvent)) return;
        FlowableActivityEvent activityEvent = (FlowableActivityEvent) event;

        String activityId = activityEvent.getActivityId();
        String processInstanceId = activityEvent.getProcessInstanceId();

        try {
            prepareMultiInstanceCollection(processInstanceId, activityId);
        } catch (Exception e) {
            log.error("多实例集合变量运行时准备失败: processInstanceId={}, activityId={}", processInstanceId, activityId, e);
        }
    }

    /**
     * 运行时兜底：当多实例节点集合变量不存在时，按节点配置补充设置。
     * <p>
     * 通过流程定义定位流程配置与节点配置，校验为多实例节点后解析执行人并写入变量。
     *
     * @param processInstanceId 流程实例ID
     * @param activityId        活动（节点）ID
     * @throws Exception 查询流程实例或解析配置失败时抛出
     */
    private void prepareMultiInstanceCollection(String processInstanceId, String activityId) throws Exception {
        String processDefinitionId = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult()
                .getProcessDefinitionId();

        String processKey = processDefinitionId.substring(0, processDefinitionId.indexOf(":"));
        ProcessDefinitionConfig config = processMapper.findByProcessKey(processKey).orElse(null);
        if (config == null) return;

        NodeConfig nodeConfig = nodeConfigMapper.selectByNodeIdAndProcessId(activityId, config.getId());
        if (nodeConfig == null || nodeConfig.getConfigJson() == null) return;

        Map<String, Object> miConfig = objectMapper.readValue(nodeConfig.getConfigJson(), Map.class);
        if (!Boolean.TRUE.equals(miConfig.get("multiInstance"))) return;

        String collectionExpr = (String) miConfig.get("collection");
        if (collectionExpr == null || !collectionExpr.startsWith("${") || !collectionExpr.endsWith("}")) return;

        String varName = collectionExpr.substring(2, collectionExpr.length() - 1);

        // 运行时兜底：只有变量不存在时才补充设置
        if (runtimeService.getVariable(processInstanceId, varName) != null) return;

        List<String> userIds = resolveAssignees(nodeConfig.getId());
        if (!userIds.isEmpty()) {
            runtimeService.setVariable(processInstanceId, varName, userIds);
            log.info("多实例集合变量运行时补充设置: processInstanceId={}, activityId={}, varName={}, users={}",
                    processInstanceId, activityId, varName, userIds);
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }
}
