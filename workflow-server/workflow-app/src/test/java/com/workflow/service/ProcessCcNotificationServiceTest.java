package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.contracts.outbox.model.OutboxEvent;
import com.workflow.contracts.outbox.model.OutboxPublishRequest;
import com.workflow.contracts.outbox.port.OutboxPublishPort;
import com.workflow.contracts.process.cc.spi.CcNotificationChannelProvider;
import com.workflow.process.cc.application.CcNotificationPayload;
import com.workflow.process.cc.application.ProcessCcNotificationOutboxHandler;
import com.workflow.process.cc.application.ProcessCcNotificationPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessCcNotificationServiceTest {

    @Test
    void publishesOneIdempotentEventPerChannel() {
        OutboxPublishPort outboxPublisher =
                mock(OutboxPublishPort.class);
        ProcessCcNotificationPublisher publisher =
                new ProcessCcNotificationPublisher(outboxPublisher);
        ProcessCcRecord record = record();

        publisher.enqueue(
                record,
                List.of("in_app", "EMAIL", "email"));

        ArgumentCaptor<OutboxPublishRequest> captor =
                ArgumentCaptor.forClass(
                        OutboxPublishRequest.class);
        verify(outboxPublisher,
                org.mockito.Mockito.times(2))
                .publish(captor.capture());
        assertEquals(
                List.of("cc-1:IN_APP", "cc-1:EMAIL"),
                captor.getAllValues().stream()
                        .map(OutboxPublishRequest::eventKey)
                        .toList());
    }

    @Test
    void handlerRoutesEventToRegisteredChannel()
            throws Exception {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        ProcessCcRecordMapper recordMapper =
                mock(ProcessCcRecordMapper.class);
        CcNotificationChannelProvider channel =
                mock(CcNotificationChannelProvider.class);
        when(channel.channel()).thenReturn("EMAIL");
        ProcessCcRecord record = record();
        when(recordMapper.selectById("cc-1"))
                .thenReturn(record);
        ProcessCcNotificationOutboxHandler handler =
                new ProcessCcNotificationOutboxHandler(
                        recordMapper,
                        objectMapper,
                        List.of(channel));
        String payload = objectMapper.writeValueAsString(
                new CcNotificationPayload(
                        "cc-1",
                        "EMAIL",
                        Map.of("subject", "待审批")));

        handler.handle(new OutboxEvent(
                "outbox-1",
                ProcessCcNotificationPublisher.TOPIC,
                "cc-1:EMAIL",
                "PROCESS_CC_RECORD",
                "cc-1",
                payload,
                0,
                LocalDateTime.now()));

        var delivered = org.mockito.ArgumentCaptor.forClass(
                com.workflow.contracts.process.cc.model.CcNotification.class);
        verify(channel).send(delivered.capture(), org.mockito.ArgumentMatchers.eq(Map.of("subject", "待审批")));
        assertEquals(record.getId(), delivered.getValue().getId());
        assertEquals(record.getCcUserId(), delivered.getValue().getCcUserId());
        delivered.getValue().setCcUserId("changed-by-extension");
        assertEquals("observer", record.getCcUserId(), "渠道不能修改宿主持久化对象");
    }

    private ProcessCcRecord record() {
        ProcessCcRecord record = new ProcessCcRecord();
        record.setId("cc-1");
        record.setProcessInstanceId("process-1");
        record.setProcessName("测试流程");
        record.setNodeName("审批");
        record.setCcUserId("observer");
        record.setComment("请关注");
        return record;
    }
}
