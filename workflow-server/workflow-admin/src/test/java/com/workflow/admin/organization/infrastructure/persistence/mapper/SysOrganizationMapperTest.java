package com.workflow.admin.organization.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SysOrganizationMapperTest {
    @Test
    void ancestorsKeepPathOrderAndBindOpaqueIdsInsteadOfEmbeddingThemInSql() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), SysOrganization.class);
        var mapper = mock(SysOrganizationMapper.class, CALLS_REAL_METHODS);
        var first = organization("first");
        String specialId = "x' OR 1=1 --";
        var second = organization(specialId);
        doReturn(List.of(second, first)).when(mapper).selectList(any());

        var result = mapper.selectParentsByPath("/0/first/" + specialId + "/first/missing/");
        assertEquals(List.of(first, second), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysOrganization>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("IN"));
        assertFalse(sql.contains(specialId));
    }

    @Test
    void emptyPathsDoNotGenerateAnEmptyInClauseOrReadAllOrganizations() {
        var mapper = mock(SysOrganizationMapper.class, CALLS_REAL_METHODS);
        for (String path : new String[]{null, "", " ", "///"}) {
            assertTrue(mapper.selectParentsByPath(path).isEmpty());
        }
        verify(mapper, never()).selectList(any());
    }

    private static SysOrganization organization(String id) {
        var row = new SysOrganization();
        row.setId(id);
        return row;
    }
}
