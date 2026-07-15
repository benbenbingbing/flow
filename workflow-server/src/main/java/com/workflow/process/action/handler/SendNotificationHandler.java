package com.workflow.process.action.handler;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 示例：发送通知流程动作。
 *
 * <p>配置示例 paramsJson：</p>
 * <pre>
 * {
 *   "templateCode": "NOTIFY_MANAGER",
 *   "notifyType": "sms",
 *   "receiverExpr": "${startUserId}"
 * }
 * </pre>
 */
@Slf4j
@Component("sendNotificationHandler")
public class SendNotificationHandler implements FlowActionHandler {

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("IN_TRANSACTION", "AFTER_COMMIT");
    }

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    @Override
    public void execute(FlowActionContext ctx) {
        Object templateCode = ctx.getCustomParams().get("templateCode");
        Object notifyType = ctx.getCustomParams().get("notifyType");
        Object receiver = ctx.getCustomParams().get("receiverExpr");

        Object startUserId = ctx.getVariable("startUserId");
        Object entityData = ctx.getEntityData();

        log.info("[流程动作] 发送通知, templateCode={}, notifyType={}, receiver={}, processInstanceId={}, entityCode={}, entityDataId={}, startUserId={}",
                templateCode, notifyType, receiver, ctx.getProcessInstanceId(),
                ctx.getEntityCode(), ctx.getEntityDataId(), startUserId);
    }
}
