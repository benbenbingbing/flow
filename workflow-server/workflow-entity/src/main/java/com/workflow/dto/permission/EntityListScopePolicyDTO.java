package com.workflow.dto.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表数据范围策略 DTO。
 * 描述一条可复用的数据过滤策略，含过滤配置、审核与发布状态等。
 */
@Data
public class EntityListScopePolicyDTO {
    /** 策略 ID */
    private String id;
    /** 实体编码 */
    private String entityCode;
    /** 策略编码 */
    private String policyKey;
    /** 策略名称 */
    private String policyName;
    /** 策略描述 */
    private String description;
    /** 预设编码（内置策略标识） */
    private String presetCode;
    /** 过滤条件配置 */
    private FilterConfigDTO filterConfig;
    /** 状态（草稿/已发布等） */
    private String status;
    /** 是否启用（0-禁用，1-启用） */
    private Integer enabled;
    /** 版本号 */
    private Integer version;
    /** 是否需要审核（0-否，1-是） */
    private Integer reviewRequired;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
