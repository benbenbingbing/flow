package com.workflow.process.task.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 节点操作矩阵的运行时决策、设计器模拟和覆盖生成接口。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NodeOperationPolicyController {

    private final NodeOperationDecisionService decisionService;

    /** 返回后端计算的可用动作；前端不得自行推导授权。 */
    @GetMapping("/tasks/{taskId}/available-actions")
    public Result<Map<String, NodeOperationDecisionService.ActionDecision>> availableActions(
            @PathVariable String taskId) {
        return Result.success(decisionService.availableActions(taskId));
    }

    /** 使用草稿策略和模拟身份预览单个操作结果。 */
    @PostMapping("/node-operation-policy/simulate")
    public Result<NodeOperationDecisionService.ActionDecision> simulate(
            @RequestBody SimulationRequest request) {
        NodeOperationPolicy.Operation operation = NodeOperationPolicy.Operation.fromCode(request.operation());
        return Result.success(decisionService.simulate(request.policyJson(), operation, request.context()));
    }

    /** 生成统一配置测试中心需要执行的矩阵正反向覆盖模板。 */
    @PostMapping("/node-operation-policy/coverage")
    public Result<List<NodeOperationDecisionService.CoverageCase>> coverage(
            @RequestBody CoverageRequest request) {
        return Result.success(decisionService.generateCoverage(request.policyJson()));
    }

    public record SimulationRequest(
            String policyJson,
            String operation,
            NodeOperationDecisionService.SimulationContext context) {
    }

    public record CoverageRequest(String policyJson) {
    }
}
