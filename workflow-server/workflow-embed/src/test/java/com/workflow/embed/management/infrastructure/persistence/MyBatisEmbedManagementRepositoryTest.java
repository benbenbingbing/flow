package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ApplicationOptionRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.BindingRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.IdentityProviderOptionRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Embed 物化资源适配器的原生表单解析契约测试。 */
class MyBatisEmbedManagementRepositoryTest {

    @Test
    void applicationOptionsUseOnlyNarrowQueriesAndPreserveExpiryAndPageTotal() {
        EmbedManagementMapper mapper = mock(EmbedManagementMapper.class);
        MyBatisEmbedManagementRepository repository = new MyBatisEmbedManagementRepository(
                mapper, new ObjectMapper(), mock(EntityNewDataFormRuntimePort.class));
        LocalDateTime expiry = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(mapper.findApplicationOptions("partner", "ACTIVE", 10, 20))
                .thenReturn(List.of(new ApplicationOptionRow(
                        "app-1", "Partner", "client-1", "ACTIVE", expiry, true)));
        when(mapper.countApplicationOptions("partner", "ACTIVE")).thenReturn(21L);

        var page = repository.findApplicationOptions(
                new OptionsFilter("partner", SecurityStatus.ACTIVE, 3, 10));

        assertEquals(21, page.total());
        assertEquals(3, page.pageNum());
        assertEquals(10, page.pageSize());
        assertEquals("client-1", page.records().get(0).clientId());
        assertEquals("Partner", page.records().get(0).name());
        assertEquals(SecurityStatus.ACTIVE, page.records().get(0).status());
        assertEquals(expiry, page.records().get(0).expiresAt());
        assertTrue(page.records().get(0).embedLaunchReady());
        verify(mapper).findApplicationOptions("partner", "ACTIVE", 10, 20);
        verify(mapper).countApplicationOptions("partner", "ACTIVE");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void identityProviderOptionsNeverFetchFullProviderConfiguration() {
        EmbedManagementMapper mapper = mock(EmbedManagementMapper.class);
        MyBatisEmbedManagementRepository repository = new MyBatisEmbedManagementRepository(
                mapper, new ObjectMapper(), mock(EntityNewDataFormRuntimePort.class));
        when(mapper.findIdentityProviderOptions("provider-1", null, 20, 0))
                .thenReturn(List.of(new IdentityProviderOptionRow(
                        "provider-1", "Partner", "TRUSTED_EXTERNAL_ID", "DISABLED")));
        when(mapper.countIdentityProviderOptions("provider-1", null)).thenReturn(1L);

        var page = repository.findIdentityProviderOptions(
                new OptionsFilter("provider-1", null, 1, 20));

        assertEquals(1, page.total());
        assertEquals("provider-1", page.records().get(0).id());
        assertEquals(ProviderType.TRUSTED_EXTERNAL_ID, page.records().get(0).type());
        assertEquals(SecurityStatus.DISABLED, page.records().get(0).status());
        verify(mapper).findIdentityProviderOptions("provider-1", null, 20, 0);
        verify(mapper).countIdentityProviderOptions("provider-1", null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void bindingProjectionPreservesCurrentFlowUserReadiness() {
        EmbedManagementMapper mapper = mock(EmbedManagementMapper.class);
        MyBatisEmbedManagementRepository repository = new MyBatisEmbedManagementRepository(
                mapper, new ObjectMapper(), mock(EntityNewDataFormRuntimePort.class));
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(mapper.findBinding("binding-1")).thenReturn(new BindingRow(
                "binding-1", "app-1", "provider-1", "digest", "v1",
                "su***er", "user-1", false, "ACTIVE", 3L,
                now, null, "admin", now, "admin", now, null, null));

        var binding = repository.findBinding("binding-1");

        assertEquals("user-1", binding.flowUserId());
        assertFalse(binding.flowUserReady());
        assertEquals(SecurityStatus.ACTIVE, binding.status());
        verify(mapper).findBinding("binding-1");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void followActiveListUsesNativeNewDataResolverExactRelease() {
        EmbedManagementMapper mapper = mock(EmbedManagementMapper.class);
        EntityNewDataFormRuntimePort formRuntimePort = mock(
                EntityNewDataFormRuntimePort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MyBatisEmbedManagementRepository repository =
                new MyBatisEmbedManagementRepository(
                        mapper, objectMapper, formRuntimePort);
        when(mapper.findListTarget("expense", "default", null))
                .thenReturn(new ListTargetRow(
                        "list-1", "entity-1", "expense", "default",
                        "list-release-1", 1L, "{}", "hash"));
        when(formRuntimePort.resolveForNewData("expense"))
                .thenReturn(Optional.of(
                        new com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort.ResolvedForm(
                                "form-first", "form-release-2", 2)));
        // 用 null 让测试在进入快照解析前停止；我们只验证此处
        // 必须消费原生 resolver 返回的精确发布坐标。
        when(mapper.findFormTarget(
                "expense", "form-first", "form-release-2"))
                .thenReturn(null);
        ObjectNode target = objectMapper.createObjectNode();
        target.put("entityCode", "expense");
        target.put("listKey", "default");
        ObjectNode policy = objectMapper.createObjectNode();
        policy.put("strategy", "FOLLOW_ACTIVE");

        assertNull(repository.resolvePublishedResource(
                SurfaceType.LIST, target, policy));

        verify(formRuntimePort).resolveForNewData("expense");
        verify(mapper).findFormTarget(
                "expense", "form-first", "form-release-2");
    }
}
