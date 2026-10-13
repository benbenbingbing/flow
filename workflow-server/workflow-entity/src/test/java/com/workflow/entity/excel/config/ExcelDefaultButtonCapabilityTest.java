package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExcelDefaultButtonCapabilityTest {
    @Test void missingDefaultDisablesDefaultExportButDoesNotAffectCustomOrImport() {
        var actions = mock(EntityListActionConfigService.class);
        var configs = mock(EntityExcelConfigurationService.class);
        var lookup = mock(CurrentProcessTaskAssigneeLookup.class);
        var service = new EntityActionCapabilityService(actions, new EntityActionRuleEvaluator(List.of(), lookup),
                mock(EntityStatusMapper.class), mock(SysUserService.class), lookup);
        var availability = new ExcelButtonAvailabilityService(configs, mock(ObjectProvider.class),
                mock(ObjectProvider.class), new ObjectMapper());
        @SuppressWarnings("unchecked") ObjectProvider<ExcelButtonAvailabilityService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(availability);
        ReflectionTestUtils.setField(service, "excelAvailability", provider);
        var list = new EntityListConfig();
        var export = Map.<String,Object>of("key", "exportAll", "enabled", true);
        var imported = Map.<String,Object>of("key", "import", "enabled", true);
        var custom = Map.<String,Object>of("key", "finance_export", "type", "custom", "enabled", true);
        when(actions.resolveToolbarButtons(list, "employee")).thenReturn(List.of(export, imported, custom));
        when(actions.permissionFor(eq("employee"), anyMap())).thenReturn("entity:employee:export");
        when(configs.resolvePublished("employee", "EXPORT", "DEFAULT", null))
                .thenThrow(new BusinessConflictException("EXCEL_CONFIGURATION_CHANGED", "请先配置默认导出方案"));
        var menus = mock(SysMenuMapper.class);
        new PermissionUtil(mock(SysUserRoleMapper.class), mock(SysRoleMenuMapper.class), menus).init();
        when(menus.selectPermsByUserId("excel-user")).thenReturn(Set.of("entity:employee:export"));
        UserContext.setCurrentUser("excel-user", "excel");
        try {
            var result = service.evaluateToolbarActions("employee", list);
            assertThat(result.get("exportAll").isEnabled()).isFalse();
            assertThat(result.get("exportAll").getReason()).contains("默认导出");
            assertThat(result.get("import").isEnabled()).isTrue();
            assertThat(result.get("finance_export").isEnabled()).isTrue();
        } finally {
            UserContext.clear();
        }
        verify(configs, times(1)).resolvePublished("employee", "EXPORT", "DEFAULT", null);
        verify(configs, times(1)).resolvePublished("employee", "IMPORT", "DEFAULT", null);
        verifyNoMoreInteractions(configs);
    }
}
