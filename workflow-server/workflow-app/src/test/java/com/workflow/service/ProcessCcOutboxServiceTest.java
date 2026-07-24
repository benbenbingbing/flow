package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessCcOutbox;
import com.workflow.mapper.ProcessCcOutboxMapper;
import com.workflow.mapper.ProcessCcRecordMapper;
import com.workflow.service.cc.CcNotificationChannel;
import com.workflow.service.cc.ProcessCcOutboxService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * 流程知会 Outbox 服务测试。
 *
 * <p>被测对象：{@link ProcessCcOutboxService}，覆盖知会消息出箱派发时已被其他节点抢占认领的场景。
 */
class ProcessCcOutboxServiceTest {
    /**
     * 测试派发时若消息已被其他节点抢占认领则跳过：验证 claim 返回 0 后不触发记录与通知渠道。
     */
    @Test
    void skipsMessageAlreadyClaimedByAnotherNode() {
        ProcessCcOutboxMapper outboxMapper = mock(ProcessCcOutboxMapper.class);
        ProcessCcRecordMapper recordMapper = mock(ProcessCcRecordMapper.class);
        CcNotificationChannel channel = mock(CcNotificationChannel.class);
        ProcessCcOutbox outbox = new ProcessCcOutbox();
        outbox.setId("outbox-1");
        when(outboxMapper.claim("outbox-1")).thenReturn(0);

        ProcessCcOutboxService service = new ProcessCcOutboxService(
                outboxMapper,
                recordMapper,
                new ObjectMapper(),
                List.of(channel));
        service.dispatchOne(outbox);

        verify(outboxMapper).claim("outbox-1");
        verifyNoInteractions(recordMapper, channel);
    }
}
