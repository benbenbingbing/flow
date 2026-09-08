package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定用户组运行时查询的状态边界。
 */
class SysUserGroupMapperSqlContractTest {

    /** 权限匹配只能获取启用且未删除的用户组。 */
    @Test
    void permissionGroupLookupExcludesDisabledAndDeletedGroups()
            throws Exception {
        Method method = SysUserGroupMapper.class.getDeclaredMethod(
                "selectGroupIdsByUserId", String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(sql.contains("inner join sys_group g on g.id = ug.group_id"));
        assertTrue(sql.contains("g.deleted = 0"));
        assertTrue(sql.contains("g.status = '0'"));
    }
}
