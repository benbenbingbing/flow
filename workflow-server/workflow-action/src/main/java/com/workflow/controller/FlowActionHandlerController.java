package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.FlowActionDefinitionRequest;
import com.workflow.dto.FlowActionHandlerOptionDTO;
import com.workflow.service.FlowActionDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程动作处理器查询接口。
 */
@RestController
@RequestMapping("/api/process-action-handlers")
@RequiredArgsConstructor
public class FlowActionHandlerController {

    private final FlowActionDefinitionService definitionService;

    /**
     * 获取所有已注册的 FlowActionHandler Bean。
     */
    @GetMapping
    public Result<List<FlowActionHandlerOptionDTO>> listHandlers(
            @RequestParam(required = false) String processConfigId) {
        return Result.success(definitionService.listVisible(processConfigId));
    }

    @GetMapping("/configs")
    public Result<List<FlowActionHandlerOptionDTO>> listConfigs() {
        return Result.success(definitionService.listAllForAdmin());
    }

    @PutMapping("/configs/{beanName}")
    public Result<FlowActionHandlerOptionDTO> saveConfig(
            @PathVariable String beanName,
            @Valid @RequestBody FlowActionDefinitionRequest request) {
        return Result.success(definitionService.save(beanName, request));
    }
}
