package com.workflow.service.permission;

import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionSqlBuilderTest {

    private final PermissionSqlBuilder builder = new PermissionSqlBuilder(new PermissionVariableResolver());

    @Test
    void buildFilterSqlRejectsInvalidFieldName() {
        SysUser user = user("u'1", "dept-1");
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("PERSONAL");
        FilterConfigDTO.FieldMappingDTO mapping = new FilterConfigDTO.FieldMappingDTO();
        mapping.setUserField("created_by OR 1=1");
        filter.setFieldMapping(mapping);

        String sql = builder.buildFilterSql(filter, user);

        assertEquals("1=0", sql);
    }

    @Test
    void buildFilterSqlEscapesValuesAndAddsStatusLimit() {
        SysUser user = user("u'1", "dept-1");
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("PERSONAL");
        FilterConfigDTO.StatusLimitDTO statusLimit = new FilterConfigDTO.StatusLimitDTO();
        statusLimit.setEnabled(true);
        statusLimit.setValues(List.of("PENDING", "A'B"));
        filter.setStatusLimit(statusLimit);

        String sql = builder.buildFilterSql(filter, user);

        assertEquals("created_by = 'u''1' AND status IN ('PENDING','A''B')", sql);
    }

    private SysUser user(String id, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setDeptId(deptId);
        return user;
    }
}
