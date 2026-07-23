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

    /**
     * 返回该动作支持的执行时机。
     *
     * @return 支持的执行时机集合（事务内、事务提交后）
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("IN_TRANSACTION", "AFTER_COMMIT");
    }

    /**
     * 返回推荐的执行时机。
     *
     * @return 推荐在事务提交后执行
     */
    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    /**
     * 执行发送通知动作。
     *
     * <p>从上下文中读取模板编码、通知类型与接收人表达式等参数，
     * 并记录通知发送日志。</p>
     *
     * @param ctx 流程动作上下文
     */
    @Override
    public void execute(FlowActionContext ctx) {
        // 读取自定义通知参数
        Object templateCode = ctx.getCustomParams().get("templateCode");
        Object notifyType = ctx.getCustomParams().get("notifyType");
        Object receiver = ctx.getCustomParams().get("receiverExpr");

        // 读取流程与实体上下文信息
        Object startUserId = ctx.getVariable("startUserId");
        Object entityData = ctx.getEntityData();

        log.info("[流程动作] 发送通知, templateCode={}, notifyType={}, receiver={}, processInstanceId={}, entityCode={}, entityDataId={}, startUserId={}",
                templateCode, notifyType, receiver, ctx.getProcessInstanceId(),
                ctx.getEntityCode(), ctx.getEntityDataId(), startUserId);
    }
}
