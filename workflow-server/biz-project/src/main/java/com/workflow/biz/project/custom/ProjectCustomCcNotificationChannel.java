package com.workflow.biz.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.process.cc.application.CcNotificationChannel;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 流程知会通知渠道示例。
 *
 * <p>渠道为 {@value #CHANNEL}。当前只记录知会记录和消息字段，不发送邮件、
 * IM 或短信，适合验证自定义渠道路由。</p>
 */
@Slf4j
@Component
public class ProjectCustomCcNotificationChannel
        implements CcNotificationChannel {

    public static final String CHANNEL =
            "PROJECT_LOG";

    /**
     * 生成通道文本，供后续匹配或展示。
     *
     * @return 处理后的通道文本，供调用方比较或展示
     */
    @Override
    public String channel() {
        return CHANNEL;
    }

    /**
     * 发送项目自定义抄送通知通道；后续由接收方或异步任务继续处理。
     *
     * @param record 记录，作为 {@code LogValue.safe} 的输入影响后续处理
     * @param message 消息，供本方法发送项目自定义抄送通知通道时使用
     */
    @Override
    public void send(
            ProcessCcRecord record,
            Map<String, Object> message) {
        log.info(
                "项目知会通知渠道执行: channel={}, ccRecordId={}, processInstanceId={}, nodeId={}, ccUserId={}, messageKeys={}",
                CHANNEL,
                LogValue.safe(record == null
                        ? null : record.getId()),
                LogValue.safe(record == null
                        ? null
                        : record.getProcessInstanceId()),
                LogValue.safe(record == null
                        ? null : record.getNodeId()),
                LogValue.safe(record == null
                        ? null : record.getCcUserId()),
                message == null
                        ? java.util.Set.of()
                        : message.keySet());
    }
}
