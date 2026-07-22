package com.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TaskCcRequest {
    @NotEmpty(message = "知会人员不能为空")
    private List<String> userIds;
    private String comment;
}
