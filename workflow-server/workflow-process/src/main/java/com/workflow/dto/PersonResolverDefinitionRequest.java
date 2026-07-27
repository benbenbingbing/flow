package com.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 人员解析器目录保存请求。
 */
@Data
public class PersonResolverDefinitionRequest {

    @NotBlank(message = "人员接口名称不能为空")
    private String displayName;
    private String description;
    private Boolean enabled;
}
