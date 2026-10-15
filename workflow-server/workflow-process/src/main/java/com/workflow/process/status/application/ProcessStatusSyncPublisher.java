package com.workflow.process.status.application;

import com.workflow.contracts.outbox.model.OutboxPublishRequest;
import com.workflow.contracts.outbox.port.OutboxPublishPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将任务完成与流程结束状态变化写入 Outbox，由异步消费者同步实体状态。
 * 同一业务事件使用稳定去重键，避免引擎监听器重放时重复推送。
 */
@Component
@RequiredArgsConstructor
public class ProcessStatusSyncPublisher {

    /** 消费者按此主题路由状态同步事件，发布与订阅必须保持一致。 */
    public static final String TOPIC = "PROCESS_STATUS_SYNC";

    private final OutboxPublishPort outboxPublisher;

    /**
     * 任务完成时发布指定目标状态；taskId 作为事件序号区分同一流程的多个任务。
     *
     * @param processInstanceId 流程实例 ID，参与事件去重和消费者定位
     * @param taskId 已完成任务 ID，构成任务事件的稳定序号
     * @param entityCode 需要同步状态的业务实体编码
     * @param entityRecordId 需要同步状态的业务记录 ID
     * @param targetStatus 任务节点决定的目标状态，供消费者更新实体
     */
    public void publishTaskStatus(
            String processInstanceId,
            String taskId,
            String entityCode,
            String entityRecordId,
            String targetStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "TASK_COMPLETED",
                taskId,
                entityCode,
                entityRecordId,
                targetStatus,
                null,
                null));
    }

    /**
     * 流程结束时发布状态类别及兜底状态，由消费者确定实体最终状态。
     *
     * @param processInstanceId 流程实例 ID，构成结束事件的去重键
     * @param entityCode 待更新实体编码
     * @param entityRecordId 待更新记录 ID
     * @param statusCategory 结束原因类别，供消费者选择状态映射
     * @param fallbackStatus 未命中映射时供消费者采用的兜底状态
     */
    public void publishProcessEnd(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "PROCESS_END",
                "END",
                entityCode,
                entityRecordId,
                null,
                statusCategory,
                fallbackStatus));
    }

    /**
     * 对账补建缺失的结束事件；已存在事件由 Outbox 自身退避重试。
     * 不重置 FAILED 的次数和下次重试时间，也不自动激活 DEAD，避免无限重投。
     *
     * @param processInstanceId 原结束事件所属流程实例 ID
     * @param entityCode 原事件的实体编码
     * @param entityRecordId 原事件的记录 ID
     * @param statusCategory 原结束原因类别
     * @param fallbackStatus 缺失事件采用的兜底状态，已存在事件保持原载荷
     */
    public void republishProcessEnd(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "PROCESS_END",
                "END",
                entityCode,
                entityRecordId,
                null,
                statusCategory,
                fallbackStatus));
    }

    /**
     * 校验路由坐标并构造 Outbox 去重键，普通发布和定时对账均保持严格幂等。
     *
     * @param payload 包含流程、任务或结束序号及实体目标的事件
     * @throws IllegalArgumentException 流程或实体坐标缺失时抛出
     */
    private void publish(ProcessStatusSyncPayload payload) {
        require(payload.processInstanceId(), "processInstanceId");
        require(payload.eventSequence(), "eventSequence");
        require(payload.entityCode(), "entityCode");
        require(payload.entityRecordId(), "entityRecordId");
        // 同一流程的结束事件固定为 END，任务事件用 taskId；重试保持相同键。
        OutboxPublishRequest request = new OutboxPublishRequest(
                TOPIC,
                payload.processInstanceId()
                        + ":" + payload.eventType()
                        + ":" + payload.eventSequence(),
                "PROCESS_INSTANCE",
                payload.processInstanceId(),
                payload,
                20);
        outboxPublisher.publish(request);
    }

    /**
     * 缺少实体或流程坐标时拒绝入队，避免消费者收到无法定位目标的事件。
     *
     * @param value 待校验的事件坐标值
     * @param field 缺失时写入异常信息的字段名
     * @throws IllegalArgumentException 值为空时抛出
     */
    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "状态同步事件缺少 " + field);
        }
    }
}
