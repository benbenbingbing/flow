package com.workflow.service.cc;

import com.workflow.entity.ProcessCcOutbox;
import com.workflow.entity.ProcessCcRecord;
import org.springframework.stereotype.Component;

/**
 * 站内信知会通知渠道。
 *
 * <p>知会记录本身（process_cc_record）即作为站内知会收件箱数据，因此该渠道的
 * 发送动作无需额外操作，发送箱仅负责统一维护发送状态。</p>
 */
@Component
public class InAppCcNotificationChannel implements CcNotificationChannel {
    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    public void send(ProcessCcRecord record, ProcessCcOutbox outbox) {
        // process_cc_record 本身就是站内知会收件箱，Outbox 只负责统一发送状态。
    }
}
