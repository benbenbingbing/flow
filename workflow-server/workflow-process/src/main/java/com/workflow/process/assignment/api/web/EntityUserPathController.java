package com.workflow.process.assignment.api.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.entity.port.EntityUserPathPort;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 设计期关系元数据目录；起点从流程绑定推导，不提供业务记录或运行时越权读取接口。 */
@RestController
@RequestMapping("/api/process/{processId}/user-relation-path")
@RequiresPermission("process:definition:manage")
@RequiredArgsConstructor
public class EntityUserPathController {
    private final EntityCodeCatalogPort entities;
    private final EntityUserPathPort paths;
    private final ObjectMapper json;

    /** 逐级读取已发布关系选项；prefix 是路径 JSON，最多八跳。 */
    @GetMapping("/options")
    public Result<List<EntityUserPathPort.Option>> options(@PathVariable String processId,
            @RequestParam(defaultValue = "[]") String prefix) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (prefix.length() > 10000) throw new IllegalArgumentException("关系路径过长");
        List<EntityUserPathPort.Step> steps = json.readValue(prefix, new TypeReference<>() { });
        return Result.success(paths.options(root(processId), steps));
    }

    /** 校验完整人员路径，返回整条路径基数，供保存前复核；不读取任何业务数据。 */
    @GetMapping
    public Result<EntityUserPathPort.PathDescription> describe(@PathVariable String processId,
            @RequestParam String path) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (path.length() > 10000) throw new IllegalArgumentException("关系路径过长");
        return Result.success(paths.describe(root(processId), json.readValue(path, new TypeReference<>() { }), List.of()));
    }
    private String root(String processId) {
        String root = entities.findEntityCodeByProcessDefinitionId(processId);
        if (root == null || root.isBlank()) throw new IllegalArgumentException("当前流程尚未绑定实体");
        return root;
    }
}
