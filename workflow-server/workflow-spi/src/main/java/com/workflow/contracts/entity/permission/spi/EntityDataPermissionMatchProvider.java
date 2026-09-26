package com.workflow.contracts.entity.permission.spi;

import com.workflow.contracts.entity.permission.model.PermissionMatchConfig;
import com.workflow.contracts.identity.model.IdentityUser;

/**
 * 数据权限匹配范围扩展点。
 */
public interface EntityDataPermissionMatchProvider {

    /**
     * 自定义范围类型。
     *
     * @return 读取后的作用域类型文本，供调用方比较或展示
     */
    String getScopeType();

    /**
     * 校验匹配条件配置合法性，默认空实现。
     *
     * @param condition 匹配条件
     */
    default void validate(PermissionMatchConfig.MatchConditionDTO condition) {
    }

    /**
     * 判断用户是否命中自定义匹配条件。
     *
     * @param condition 匹配条件
     * @param user      当前用户
     * @return 命中返回 true
     */
    boolean matches(PermissionMatchConfig.MatchConditionDTO condition, IdentityUser user);
}
