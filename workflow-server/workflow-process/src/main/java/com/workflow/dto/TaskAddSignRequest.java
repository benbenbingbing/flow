package com.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 任务加签请求 DTO。
 *
 * <p>用于在审批过程中对当前任务进行加签操作，支持并行、前加签、后加签三种模式。</p>
 */
@Data
public class TaskAddSignRequest {

    /**
     * 加签类型：PARALLEL（并行）、BEFORE（前加签）、AFTER（后加签）
     */
    @Pattern(regexp = "PARALLEL|BEFORE|AFTER", message = "加签类型仅支持PARALLEL、BEFORE、AFTER")
    private String type = "PARALLEL";

    /**
     * 加签人员标识列表（用户ID或用户名）
     */
    @NotEmpty(message = "加签人员不能为空")
    private List<String> userIds;

    /**
     * 加签备注/说明
     */
    private String comment;

    /**
     * 加签完成策略，目前仅支持 ALL（全部完成）
     */
    private String completionPolicy = "ALL";
}
