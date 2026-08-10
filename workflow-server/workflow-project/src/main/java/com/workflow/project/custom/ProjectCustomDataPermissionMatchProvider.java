package com.workflow.project.custom;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.permission.api.response.MatchConfigDTO;
import com.workflow.entity.permission.application.EntityDataPermissionMatchProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限适用用户匹配扩展示例。
 *
 * <p>范围类型为 {@value #SCOPE_TYPE}，目标值可填写用户 ID 或用户名。</p>
 */
@Slf4j
@Component
public class ProjectCustomDataPermissionMatchProvider
        implements EntityDataPermissionMatchProvider {

    public static final String SCOPE_TYPE =
            "PROJECT:CUSTOM_MATCH";

    @Override
    public String getScopeType() {
        return SCOPE_TYPE;
    }

    @Override
    public void validate(
            MatchConfigDTO.MatchConditionDTO condition) {
        if (condition == null
                || condition.getTargetIds() == null
                || condition.getTargetIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "项目自定义用户范围至少选择一个用户");
        }
        String operator = condition.getOperator() == null
                ? "ANY"
                : condition.getOperator().toUpperCase();
        if (!Set.of("ANY", "ALL").contains(operator)) {
            throw new IllegalArgumentException(
                    "项目自定义用户范围仅支持 ANY 或 ALL");
        }
    }

    @Override
    public boolean matches(
            MatchConfigDTO.MatchConditionDTO condition,
            SysUser user) {
        if (condition == null || user == null) {
            return false;
        }
        validate(condition);
        Set<String> identities =
                new LinkedHashSet<>();
        add(identities, user.getId());
        add(identities, user.getUsername());
        List<String> targets =
                condition.getTargetIds();
        boolean matched = "ALL".equalsIgnoreCase(
                condition.getOperator())
                ? identities.containsAll(targets)
                : targets.stream()
                        .anyMatch(identities::contains);
        log.info(
                "项目数据权限用户范围匹配: scopeType={}, userId={}, targetCount={}, operator={}, matched={}",
                SCOPE_TYPE,
                LogValue.safe(user.getId()),
                targets.size(),
                condition.getOperator(),
                matched);
        return matched;
    }

    private void add(
            Set<String> values,
            String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }
}
