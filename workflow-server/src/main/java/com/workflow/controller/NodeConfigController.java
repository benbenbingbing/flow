package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.NodeConfigDTO;
import com.workflow.service.NodeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节点配置控制器
 * 
 * @description 提供流程节点配置的RESTful API接口
 *              包括节点配置的查询、创建、删除等操作
 * @author Workflow Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/process/{processId}/nodes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NodeConfigController {

    /**
     * 节点配置服务
     */
    private final NodeConfigService nodeService;

    /**
     * 获取流程的所有节点配置
     * 
     * @param processId 流程定义ID
     * @return 节点配置列表
     */
    @GetMapping
    public ApiResponse<List<NodeConfigDTO>> list(@PathVariable String processId) {
        return ApiResponse.success(nodeService.findByProcessId(processId));
    }

    /**
     * 根据ID获取节点配置详情
     * 
     * @param id 节点配置ID
     * @return 节点配置详情
     */
    @GetMapping("/{id}")
    public ApiResponse<NodeConfigDTO> getById(@PathVariable String id) {
        return ApiResponse.success(nodeService.findById(id));
    }

    /**
     * 为流程创建节点配置
     * 
     * @param processId 流程定义ID
     * @param dto 节点配置数据
     * @return 创建后的节点配置
     */
    @PostMapping
    public ApiResponse<NodeConfigDTO> create(@PathVariable String processId, @RequestBody NodeConfigDTO dto) {
        return ApiResponse.success(nodeService.save(processId, dto));
    }

    /**
     * 删除节点配置
     * 
     * @param id 节点配置ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        nodeService.delete(id);
        return ApiResponse.success();
    }
}
