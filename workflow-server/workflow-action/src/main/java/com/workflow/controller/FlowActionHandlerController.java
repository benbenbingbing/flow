package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.FlowActionDefinitionRequest;
import com.workflow.dto.FlowActionHandlerOptionDTO;
import com.workflow.service.FlowActionDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
     * 查询当前流程配置下可见且启用的处理器列表。
     *
     * @param processConfigId 流程配置 ID；为空则不按实体过滤
     * @return 可见处理器选项列表
     */
    @GetMapping
    public Result<List<FlowActionHandlerOptionDTO>> listHandlers(
            @RequestParam(required = false) String processConfigId) {
        return Result.success(definitionService.listVisible(processConfigId));
    }

    /**
     * 查询全部处理器配置（含未启用的），仅超级管理员可调用。
     *
     * @return 全部处理器选项列表
     */
    @GetMapping("/configs")
    public Result<List<FlowActionHandlerOptionDTO>> listConfigs() {
        return Result.success(definitionService.listAllForAdmin());
    }

    /**
     * 保存处理器目录配置（中文名称、可见范围、实体绑定）。
     *
     * @param beanName 处理器 Bean 名称
     * @param request  配置请求体
     * @return 保存后的处理器选项
     */
    @PostMapping("/configs/{beanName}")
    public Result<FlowActionHandlerOptionDTO> saveConfig(
            @PathVariable String beanName,
            @Valid @RequestBody FlowActionDefinitionRequest request) {
        return Result.success(definitionService.save(beanName, request));
    }
}
