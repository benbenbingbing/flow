package com.workflow.process.cc.application;

import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 站内信知会通知渠道。
 *
 * <p>知会记录本身（process_cc_record）即作为站内知会收件箱数据，因此该渠道的
 * 发送动作无需额外操作，通用 Outbox 仅负责统一维护投递状态。</p>
 */
@Component
public class InAppCcNotificationChannel implements CcNotificationChannel {
    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    public void send(
            ProcessCcRecord record,
            Map<String, Object> message) {
        // process_cc_record 本身就是站内知会收件箱，Outbox 只负责统一发送状态。
    }
}
