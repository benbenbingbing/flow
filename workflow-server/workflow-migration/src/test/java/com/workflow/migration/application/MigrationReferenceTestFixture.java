package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

/** 既有局部服务测试装配真实引用转换器，继续复用各测试已有的目录 mock。 */
final class MigrationReferenceTestFixture {
    static void attachTo(Object service) {
        ConfigMigrationReferenceService references = new ConfigMigrationReferenceService(
                dependency(service, "userMapper", SysUserMapper.class),
                dependency(service, "roleMapper", SysRoleMapper.class),
                dependency(service, "groupMapper", SysGroupMapper.class),
                dependency(service, "organizationMapper", SysOrganizationMapper.class),
                mock(FlowActionDefinitionMapper.class), mock(FlowActionCatalogPort.class));
        ConfigMigrationSubFormReferences forms = new ConfigMigrationSubFormReferences(
                dependency(service, "entityMapper", EntityDefinitionMapper.class),
                dependency(service, "formMapper", EntityFormMapper.class), mock(UiConfigReleaseMapper.class),
                references, mock(ObjectProvider.class), new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "referenceService", references);
        ReflectionTestUtils.setField(service, "subFormReferences", forms);
    }

    private static <T> T dependency(Object service, String name, Class<T> type) {
        try {
            T value = type.cast(ReflectionTestUtils.getField(service, name));
            return value == null ? mock(type) : value;
        } catch (IllegalArgumentException ignored) {
            return mock(type);
        }
    }
}
