package com.workflow.process.cc.application;

import com.workflow.contracts.process.cc.spi.CcNotificationChannelProvider;

import com.workflow.contracts.process.cc.model.CcNotification;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 站内信知会通知渠道。
 *
 * <p>知会记录本身（process_cc_record）即作为站内知会收件箱数据，因此该渠道的
 * 发送动作无需额外操作，通用 Outbox 仅负责统一维护投递状态。</p>
 */
@Component
public class InAppCcNotificationChannel implements CcNotificationChannelProvider {
    /**
     * 生成通道文本，供后续匹配或展示。
     *
     * @return 处理后的通道文本，供调用方比较或展示
     */
    @Override
    public String channel() {
        return "IN_APP";
    }

    /**
     * 发送{@code app}抄送通知通道；后续由接收方或异步任务继续处理。
     *
     * @param record 记录，供本方法发送{@code app}抄送通知通道时使用
     * @param message 消息，供本方法发送{@code app}抄送通知通道时使用
     */
    @Override
    public void send(
            CcNotification record,
            Map<String, Object> message) {
        // process_cc_record 本身就是站内知会收件箱，Outbox 只负责统一发送状态。
    }
}
