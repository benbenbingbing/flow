package com.workflow.service.cc;

import com.workflow.entity.ProcessCcOutbox;
import com.workflow.entity.ProcessCcRecord;

public interface CcNotificationChannel {
    String channel();

    void send(ProcessCcRecord record, ProcessCcOutbox outbox);
}
