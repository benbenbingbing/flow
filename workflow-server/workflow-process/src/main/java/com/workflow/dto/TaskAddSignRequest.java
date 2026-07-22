package com.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class TaskAddSignRequest {
    @Pattern(regexp = "PARALLEL|BEFORE|AFTER", message = "加签类型仅支持PARALLEL、BEFORE、AFTER")
    private String type = "PARALLEL";
    @NotEmpty(message = "加签人员不能为空")
    private List<String> userIds;
    private String comment;
    private String completionPolicy = "ALL";
}
