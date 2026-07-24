package com.workflow.service.permission;

import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.SysUser;

/**
 * 数据权限匹配范围扩展点。
 */
public interface EntityDataPermissionMatchProvider {

    /**
     * 自定义范围类型。
     */
    String getScopeType();

    /**
     * 校验匹配条件配置合法性，默认空实现。
     *
     * @param condition 匹配条件
     */
    default void validate(MatchConfigDTO.MatchConditionDTO condition) {
    }

    /**
     * 判断用户是否命中自定义匹配条件。
     *
     * @param condition 匹配条件
     * @param user      当前用户
     * @return 命中返回 true
     */
    boolean matches(MatchConfigDTO.MatchConditionDTO condition, SysUser user);
}
