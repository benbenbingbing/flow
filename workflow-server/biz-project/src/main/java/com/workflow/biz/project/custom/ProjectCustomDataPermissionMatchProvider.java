package com.workflow.biz.project.custom;

import com.workflow.contracts.identity.model.IdentityUser;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.permission.model.PermissionMatchConfig;
import com.workflow.contracts.entity.permission.spi.EntityDataPermissionMatchProvider;
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

    /**
     * 读取作用域类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的作用域类型文本，供调用方比较或展示
     */
    @Override
    public String getScopeType() {
        return SCOPE_TYPE;
    }

    /**
     * 校验项目自定义数据权限匹配提供者；不满足约束时阻止后续处理。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public void validate(
            PermissionMatchConfig.MatchConditionDTO condition) {
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

    /**
     * 判断是否匹配项目自定义数据权限匹配提供者；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 项目自定义数据权限匹配提供者条件成立时为 true，否则为 false
     */
    @Override
    public boolean matches(
            PermissionMatchConfig.MatchConditionDTO condition,
            IdentityUser user) {
        if (condition == null || user == null) {
            return false;
        }
        validate(condition);
        Set<String> identities =
                new LinkedHashSet<>();
        add(identities, user.id());
        add(identities, user.username());
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
                LogValue.safe(user.id()),
                targets.size(),
                condition.getOperator(),
                matched);
        return matched;
    }

    /**
     * 添加项目自定义数据权限匹配提供者；结果供后续流程传递或持久化。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param value 待添加项目自定义数据权限匹配提供者的原始输入，结果供调用方继续使用
     */
    private void add(
            Set<String> values,
            String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }
}
