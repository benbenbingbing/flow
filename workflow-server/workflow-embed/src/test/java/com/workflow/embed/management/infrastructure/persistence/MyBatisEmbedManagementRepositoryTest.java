package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.entity.EntityNewDataFormRuntimePort;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Embed 物化资源适配器的原生表单解析契约测试。 */
class MyBatisEmbedManagementRepositoryTest {

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
                        new EntityNewDataFormRuntimePort.ResolvedForm(
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
