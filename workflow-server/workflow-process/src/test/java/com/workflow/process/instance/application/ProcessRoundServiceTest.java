package com.workflow.process.instance.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEndReason;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessRoundServiceTest {
    @Test void oldRoundKeepsItsOwnEndFactAndUnreadableRoundsAreNotDisclosed() {
        var mapper = mock(EntityProcessLinkMapper.class);
        var history = mock(HistoryService.class);
        var access = mock(ProcessInstanceAccessService.class);
        var service = new ProcessRoundService(mapper, history, access);
        var old = link("old", 1); var current = link("new", 2);
        when(mapper.findByProcessInstanceId("old")).thenReturn(old);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(current, old));
        doThrow(new ForbiddenException("无权读取新一轮")).when(access).requireReadAccess("new");
        var query = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        var historic = mock(HistoricProcessInstance.class);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(historic);
        when(historic.getEndTime()).thenReturn(new Date());
        when(historic.getDeleteReason()).thenReturn(ProcessEndReason.encode("WITHDRAWN", "修改后重新提交"));
        var rounds = service.listReadableRounds("old");
        assertEquals(1, rounds.size());
        assertEquals("old", rounds.get(0).processInstanceId());
        assertEquals("COMPLETED", rounds.get(0).status());
        assertEquals("WITHDRAWN", rounds.get(0).endType());
        verify(query, never()).processInstanceId("new");
    }

    private EntityProcessLink link(String instanceId, int generation) {
        var link = new EntityProcessLink(); link.setEntityCode("expense"); link.setEntityRecordId("record");
        link.setProcessInstanceId(instanceId); link.setGeneration(generation); return link;
    }
}
