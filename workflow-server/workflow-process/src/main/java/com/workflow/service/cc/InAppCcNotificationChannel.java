package com.workflow.service.cc;

import com.workflow.entity.ProcessCcOutbox;
import com.workflow.entity.ProcessCcRecord;
import org.springframework.stereotype.Component;

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
