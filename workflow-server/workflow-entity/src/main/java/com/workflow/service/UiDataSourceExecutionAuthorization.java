package com.workflow.service;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.entity.SysUser;

import java.util.Map;

/**
 * UI 数据源执行授权结果。
 *
 * <p>由 {@link UiDataSourceExecutionAccessService} 在预览或发布链路完成来源校验、
 * 数据范围权限计算和可信上下文清洗后生成，作为数据源执行的不可变授权凭证。</p>
 *
 * @param preview        是否为草稿预览执行
 * @param configType     配置来源类型（FORM 或 LIST）
 * @param configId       配置来源ID
 * @param releaseId      发布记录ID，预览时为 null
 * @param releaseVersion 发布版本号，预览时为 null
 * @param bindingPath    数据源绑定路径（JSON 指针），用于审计
 * @param usage          数据源使用位置，如 LIST_QUERY、LIST_COLUMN
 * @param entityId       关联实体ID
 * @param entityCode     关联实体编码
 * @param listKey        关联列表编码，表单来源时为 null
 * @param user           已认证用户
 * @param dataScopePlan  数据范围权限计划
 * @param requestContext 清洗后的请求上下文（已剔除保留字段）
 * @param idempotencySeed 幂等种子，来自服务端可信提交链路
 */
public record UiDataSourceExecutionAuthorization(
        boolean preview,
        String configType,
        String configId,
        String releaseId,
        Integer releaseVersion,
        String bindingPath,
        String usage,
        String entityId,
        String entityCode,
        String listKey,
        SysUser user,
        DataScopePlan dataScopePlan,
        Map<String, Object> requestContext,
        String idempotencySeed) {
}
