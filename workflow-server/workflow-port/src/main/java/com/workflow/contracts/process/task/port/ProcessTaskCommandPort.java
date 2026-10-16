package com.workflow.contracts.process.task.port;

import com.workflow.contracts.process.task.model.*;

/**
 * 任务办理的公共命令入口，普通审批、本地加签及源任务编排由平台判定。
 * 操作人来自宿主身份；所有变更参与调用方事务，返回不代表外层事务已提交。
 * 每次新的业务意图使用新 requestId，重试使用原值；不能通过调用顺序规避发布配置和身份校验。
 * 状态冲突、权限拒绝和发布表单不匹配通过 ProcessOperationException 的 errorCode 区分。
 */
public interface ProcessTaskCommandPort {
    /** 认领给当前候选身份；同一人已认领可返回未变化，他人已认领则冲突，本地加签不能认领。 */
    TaskCommandResult claim(ClaimTaskCommand command);
    /**
     * 按已发布动作、表单和节点策略办理明确任务。平台负责普通/加签子任务/源任务分流，
     * 调用方无需预判任务种类，也不能跳过提交按钮和下一审批人校验。
     * @param command actionCode 决定业务动作；actionLabel 仅用于展示，不能改变通过/拒绝的含义
     * @return 实际任务结果和实例生命周期；登记延后完成不等于主流程已经推进
     */
    TaskCommandResult complete(CompleteTaskCommand command);
    /** 将任务转给合法接收人，同步引擎、本地待办和日志；不得同时提交下一节点审批人选择。 */
    TaskCommandResult transfer(TransferTaskCommand command);
    /**
     * 沿发布的驳回连线退回到白名单节点，并在同一事务确认生成了目标待办；未到达则全部回滚。
     * 异步离开、加签或会签尚未汇聚时不能保证立即到达，应使用普通 reject 并查询后续状态。
     * 此方法不直接移动引擎执行位置，目标白名单也不能代替实际 BPMN 路由。
     */
    TaskCommandResult returnToNode(ReturnTaskCommand command);
    /** 在原实例的退回待办上重提，仍执行节点表单处理；与撤回后新建流程轮次的重新发起不同。 */
    TaskCommandResult resubmit(ResubmitTaskCommand command);
    /** 创建 BEFORE/AFTER/PARALLEL 加签，人员解析去重后按 ALL 策略编排；返回本地任务 ID，不能当成引擎任务 ID。 */
    AddSignResult addSign(AddSignCommand command);
    /** 撤销指定加签编排，清理子待办并按原规则恢复/推进源任务；不是删除审批历史。 */
    TaskCommandResult cancelAddSign(CancelAddSignCommand command);
    /** 按节点配置和当前身份添加人工知会。 */
    ManualCcResult addCc(ManualCcCommand command);
}
