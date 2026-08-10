package com.workflow.project.custom;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.EntityDataPermissionFilterProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据权限 SQL 条件扩展示例。
 *
 * <p>类型为 {@value #TYPE}。示例固定返回 {@code 1=0}，用于验证自定义条件
 * 编译链路，同时保持默认拒绝；真实实现必须使用白名单字段和安全参数。</p>
 */
@Slf4j
@Component
public class ProjectCustomDataPermissionFilterProvider
        implements EntityDataPermissionFilterProvider {

    public static final String TYPE =
            "PROJECT:CUSTOM_FILTER";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(
            String entityCode,
            EntityActionRuleDTO.RuleNode node) {
        log.info(
                "项目数据权限 SQL 条件校验: type={}, entityCode={}, field={}, operator={}",
                TYPE,
                LogValue.safe(entityCode),
                LogValue.safe(node == null
                        ? null : node.getField()),
                LogValue.safe(node == null
                        ? null : node.getOperator()));
    }

    @Override
    public String toSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user) {
        log.info(
                "项目数据权限 SQL 条件编译: type={}, entityCode={}, userId={}, result=DENY",
                TYPE,
                LogValue.safe(entityCode),
                LogValue.safe(user == null
                        ? null : user.getId()));
        return "1=0";
    }
}
