package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.permission.model.EntityPermissionOption;
import com.workflow.contracts.entity.permission.spi.EntityPermissionOptionProvider;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** SPI 脱离 API DTO 后，目录仍保留候选项 JSON、空值处理和权限码去重语义。 */
class EntityPermissionCatalogProviderTest {
    @Test
    void mapsProviderOptionsWithoutChangingApiFieldsOrDuplicatePrecedence() {
        var definitions = mock(EntityDefinitionMapper.class);
        var menus = mock(SysMenuMapper.class);
        var provider = mock(EntityPermissionOptionProvider.class);
        when(definitions.findByEntityCode("expense")).thenReturn(Optional.empty());
        when(menus.selectPermsByEntityCode("expense")).thenReturn(Set.of());
        String code = "entity:expense:custom:review";
        when(provider.getOptions("expense")).thenReturn(Arrays.asList(null,
                new EntityPermissionOption("review", code, "复核", "扩展复核权限", "CUSTOM"),
                new EntityPermissionOption("duplicate", code, "重复", "不应覆盖", "CUSTOM"),
                new EntityPermissionOption("empty", " ", "空权限", "忽略", "CUSTOM")));
        var service = new EntityPermissionCatalogService(definitions, mock(EntityListConfigMapper.class),
                mock(EntityStatusMapper.class), menus, mock(SysRoleMapper.class),
                mock(SysRoleMenuMapper.class), mock(EntityListActionConfigService.class),
                java.util.List.of(provider));

        var options = service.getOptions("expense").stream()
                .filter(option -> code.equals(option.getCode())).toList();
        assertEquals(1, options.size());
        assertEquals(Map.of("action", "review", "code", code, "label", "复核",
                        "description", "扩展复核权限", "category", "CUSTOM"),
                new ObjectMapper().convertValue(options.get(0), Map.class));
    }
}
