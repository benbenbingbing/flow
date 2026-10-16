package com.workflow.process.task.application;

import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 平台页面和 Java 端口共用的操作规则编排；所有必要校验留在事务应用层。 */
@Service
@RequiredArgsConstructor
public class TaskOperationApplicationService {
    private final TaskAddSignService taskAddSignService;
    private final ProcessCcRuntimeService ccRuntimeService;
    private final NodeOperationCapabilityService nodeOperationCapabilityService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NodeOperationDecisionService nodeOperationDecisionService;

    /**
     * 合并加签运行状态与发布节点策略，返回值只用于展示，不能替代提交时重新校验。
     * @param taskId 真实源任务标识；当前身份须有办理权
     * @return 原有 HTTP 操作结构，公共端口另行映射为独立契约模型
     */
    @Transactional(readOnly = true)
    public Map<String, Object> operations(String taskId) {
        Map<String, Object> operations = new LinkedHashMap<>(taskAddSignService.operations(taskId));
        operations.put("manualCc", ccRuntimeService.isManualCcAllowed(taskId));
        NodeOperationCapabilityService.OperationCapabilities capabilities =
                nodeOperationCapabilityService.availableCapabilities(taskId);

        // 固有运行状态与节点配置采用 AND，配置不能重新开放正在加签等场景下已关闭的动作。
        boolean intrinsicTransfer = Boolean.TRUE.equals(operations.get("transfer"));
        boolean intrinsicAddSign = Boolean.TRUE.equals(operations.get("addSign"));
        List<String> allowedTypes = ((List<?>) operations.getOrDefault("addSignTypes", List.of()))
                .stream()
                .map(String::valueOf)
                .filter(capabilities.allowedAddSignTypes()::contains)
                .toList();
        operations.put("transfer", intrinsicTransfer && capabilities.transfer());
        operations.put("addSignTypes", allowedTypes);
        operations.put("addSign", intrinsicAddSign
                && capabilities.addSign()
                && !allowedTypes.isEmpty());
        operations.put("terminate", capabilities.terminate());

        // 存量流程仍保留矩阵对其它旧动作的准确约束；新三开关会令旧矩阵自动失效。
        if (nodeOperationDecisionService != null) {
            Map<String, NodeOperationDecisionService.ActionDecision>
                    decisions = nodeOperationDecisionService.availableActions(taskId);
            operations.put("approve", Boolean.TRUE.equals(operations.get("approve"))
                    && decisions.get("approve").allowed());
            operations.put("reject", Boolean.TRUE.equals(operations.get("reject"))
                    && decisions.get("reject").allowed());
            operations.put("manualCc", Boolean.TRUE.equals(operations.get("manualCc"))
                    && decisions.get("manualCc").allowed());
            operations.put("withdraw", decisions.get("withdraw").allowed());
        }
        return operations;
    }

    /**
     * 人工知会必须先满足节点矩阵和人员解析规则，再由既有服务写记录并通知。
     * @return 此次新增记录数；外层事务回滚时知会记录同样回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public int manualCc(String taskId, TaskCcRequest request) {
        if (nodeOperationDecisionService != null) {
            nodeOperationDecisionService.requireAllowed(
                    taskId,
                    com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.MANUAL_CC,
                    com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext
                            .ofTarget(
                                    request.getComment(),
                                    request.getUserIds() == null
                                            ? java.util.Set.of()
                                            : new java.util.LinkedHashSet<>(request.getUserIds()),
                                    null,
                                    null,
                                    Map.of()));
        }
        return ccRuntimeService.manualCc(taskId, request);
    }
}
