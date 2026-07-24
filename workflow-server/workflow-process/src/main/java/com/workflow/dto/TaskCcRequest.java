package com.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 任务人工知会请求 DTO。
 *
 * <p>用于在任务办理过程中，由办理人手动添加知会人员（被抄送人）。</p>
 */
@Data
public class TaskCcRequest {

    /**
     * 知会人员标识列表（用户ID或用户名）
     */
    @NotEmpty(message = "知会人员不能为空")
    private List<String> userIds;

    /**
     * 知会备注/说明
     */
    private String comment;
}
