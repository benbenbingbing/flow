package com.workflow.process.definition.application;

import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 流程目录绑定门闩的锁序与发布状态测试。 */
class ProcessCatalogAdapterTest {

    @Test
    void bindingStatesUseStableLockOrderAndKeepDeletedPublishedDependencies() {
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        ProcessVersionHistoryMapper historyMapper =
                mock(ProcessVersionHistoryMapper.class);
        ProcessDefinitionConfig active = process(
                "1", "A", 0);
        ProcessDefinitionConfig deleted = process(
                "2", "B", 1);
        when(processMapper.selectAnyByIdForBindingUpdate("1"))
                .thenReturn(active);
        when(processMapper.selectAnyByIdForBindingUpdate("2"))
                .thenReturn(deleted);
        when(historyMapper.countPublishedVersions("2"))
                .thenReturn(2L);
        ProcessCatalogAdapter adapter = new ProcessCatalogAdapter(
                processMapper, historyMapper);

        var states = adapter.lockBindingStates(List.of(
                "02", "1", "2"));

        InOrder lockOrder = inOrder(processMapper);
        lockOrder.verify(processMapper)
                .selectAnyByIdForBindingUpdate("1");
        lockOrder.verify(processMapper)
                .selectAnyByIdForBindingUpdate("2");
        assertTrue(states.get("1").available());
        assertFalse(states.get("1").hasPublishedVersion());
        assertFalse(states.get("2").available());
        assertTrue(states.get("2").hasPublishedVersion());
    }

    private ProcessDefinitionConfig process(
            String id,
            String key,
            int deleted) {
        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId(id);
        process.setProcessKey(key);
        process.setProcessName(key);
        process.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        process.setDeleted(deleted);
        return process;
    }
}
