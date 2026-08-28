package com.workflow.admin.organization.application;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysOrganizationServiceImplPositionCompatibilityTest {

    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysDictItemMapper dictItemMapper =
            mock(SysDictItemMapper.class);
    private final SysOrganizationServiceImpl service =
            new SysOrganizationServiceImpl(
                    organizationMapper,
                    mock(SysUserMapper.class),
                    dictItemMapper);

    @Test
    void businessLevelIsNormalizedBeforePersistence() {
        SysOrganization existing = existingOrganization();
        when(organizationMapper.selectById(existing.getId()))
                .thenReturn(existing);
        when(dictItemMapper.selectEnabledByCode(
                "organization_business_level", "FIRST_LEVEL_DEPT"))
                .thenReturn(new SysDictItem());
        SysOrganization requested = updateRequest("  first_level_dept  ");

        service.saveOrg(requested);

        ArgumentCaptor<SysOrganization> persisted =
                ArgumentCaptor.forClass(SysOrganization.class);
        verify(organizationMapper).updateById(persisted.capture());
        assertEquals("FIRST_LEVEL_DEPT",
                persisted.getValue().getBusinessLevelCode());
        // 普通组织保存永远不直接覆盖负责人兼容投影。
        assertNull(persisted.getValue().getLeaderId());
        assertNull(persisted.getValue().getLeaderName());
    }

    @Test
    void businessLevelCanBeExplicitlyClearedWithoutClearingLeaderProjection()
            throws Exception {
        SysOrganization existing = existingOrganization();
        existing.setBusinessLevelCode("FIRST_LEVEL_DEPT");
        when(organizationMapper.selectById(existing.getId()))
                .thenReturn(existing);

        service.saveOrg(updateRequest("   "));

        ArgumentCaptor<SysOrganization> persisted =
                ArgumentCaptor.forClass(SysOrganization.class);
        verify(organizationMapper).updateById(persisted.capture());
        assertNull(persisted.getValue().getBusinessLevelCode());
        assertNull(persisted.getValue().getLeaderId());

        TableField policy = SysOrganization.class
                .getDeclaredField("businessLevelCode")
                .getAnnotation(TableField.class);
        assertNotNull(policy);
        assertEquals(FieldStrategy.ALWAYS, policy.updateStrategy());
    }

    @Test
    void businessLevelOptionsOnlyExposeEnabledDictionaryProjection() {
        SysDictItem firstLevel = new SysDictItem();
        firstLevel.setItemCode("FIRST_LEVEL_DEPT");
        firstLevel.setItemLabel("一级部门");
        firstLevel.setSort(30);
        SysDictItem team = new SysDictItem();
        team.setItemCode("TEAM");
        team.setItemLabel("团队");
        when(dictItemMapper.selectEnabledByDictCode(
                "organization_business_level"))
                .thenReturn(List.of(firstLevel, team));

        var options = service.getBusinessLevelOptions();

        assertEquals(2, options.size());
        assertEquals("FIRST_LEVEL_DEPT", options.get(0).code());
        assertEquals("一级部门", options.get(0).name());
        assertEquals(30, options.get(0).sortOrder());
        assertEquals("TEAM", options.get(1).code());
        assertEquals(0, options.get(1).sortOrder());
        verify(dictItemMapper).selectEnabledByDictCode(
                "organization_business_level");
    }

    private static SysOrganization existingOrganization() {
        SysOrganization organization = new SysOrganization();
        organization.setId("org-a");
        organization.setOrgCode("ORG_A");
        organization.setOrgName("组织 A");
        organization.setType(SysOrganization.Type.ORG.getValue());
        organization.setParentId("0");
        organization.setPath("/0/org-a/");
        organization.setLevel(0);
        organization.setStatus(SysOrganization.Status.ENABLED.getValue());
        organization.setLeaderId("leader-a");
        organization.setLeaderName("负责人 A");
        return organization;
    }

    private static SysOrganization updateRequest(String businessLevelCode) {
        SysOrganization organization = existingOrganization();
        organization.setBusinessLevelCode(businessLevelCode);
        organization.setLeaderId("leader-a");
        return organization;
    }
}
