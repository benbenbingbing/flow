package com.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 流程动作定义配置请求 DTO。
 *
 * <p>由超级管理员提交，用于新增或更新处理器目录配置。</p>
 */
@Data
public class FlowActionDefinitionRequest {

    /** 动作中文名称 */
    @NotBlank(message = "动作中文名称不能为空")
    private String displayName;

    /** 动作描述 */
    private String description;

    /** 可见范围：GLOBAL、ENTITY */
    @NotBlank(message = "可见范围不能为空")
    private String visibilityScope;

    /** 可见实体编码列表（visibilityScope 为 ENTITY 时必填） */
    private List<String> entityCodes;
    /** 是否启用 */
    private Boolean enabled;
}
