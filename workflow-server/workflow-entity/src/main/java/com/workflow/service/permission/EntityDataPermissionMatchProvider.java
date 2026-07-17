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

    default void validate(MatchConfigDTO.MatchConditionDTO condition) {
    }

    boolean matches(MatchConfigDTO.MatchConditionDTO condition, SysUser user);
}
