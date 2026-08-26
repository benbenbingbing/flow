package com.workflow.process.coordination.application;

import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 将已预览的跨流程写操作按目标拆分到通用 Outbox。 */
@Component
@RequiredArgsConstructor
public class RelatedProcessCoordinationPublisher {

    public static final String TOPIC = "RELATED_PROCESS_COORDINATION";

    private final OutboxPublisher outboxPublisher;

    /**
     * 为每个真正受影响的活动流程发布一条事件。
     *
     * <p>事件键绑定 action execution、计划指纹和目标流程实例。
     * 同一流程动作重试时不会重复产生可见副作用。</p>
     *
     * @return 新增或已存在的幂等事件数量
     */
    public int publish(
            RelatedProcessCoordinationPlan plan,
            RelatedProcessCoordinationPlan.Command command) {
        if (plan == null || !plan.writeOperation()) {
            throw new IllegalArgumentException("协同计划不是写操作");
        }
        int queued = 0;
        for (TargetImpact target : plan.targets()) {
            // 终止传播对已终态或没有流程的记录是显式无影响，
            // 不为它们创建伪写入事件。路由计划已保证唯一且活动。
            if (target.state() != ProcessState.ACTIVE) {
                continue;
            }
            if (plan.operation() != Operation.ROUTE_RELATED_PARENT
                    && plan.operation()
                            != Operation.PROPAGATE_TERMINATION) {
                throw new IllegalArgumentException("不支持的协同写操作");
            }
            String eventKey = eventKey(plan, target);
            outboxPublisher.publish(new OutboxPublishRequest(
                    TOPIC,
                    eventKey,
                    "PROCESS_INSTANCE",
                    target.processInstanceId(),
                    new RelatedProcessCoordinationEvent(
                            plan, command, target),
                    20));
            queued++;
        }
        return queued;
    }

    /** 按计划与目标生成可在消费边界重算的幂等键。 */
    static String eventKey(
            RelatedProcessCoordinationPlan plan,
            TargetImpact target) {
        String raw = plan.planId() + '\n'
                + plan.operation() + '\n'
                + target.record().entityCode() + ':'
                + target.record().recordId() + '\n'
                + target.processInstanceId();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
