package com.workflow.port.process;

import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.query.*;
import com.workflow.port.process.model.task.*;
import com.workflow.port.process.model.task.TaskInterventionModels.AddSignPreview;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnCommand;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnResult;
import com.workflow.port.process.model.task.TaskInterventionModels.HistoryReturnTarget;
import java.util.List;
import java.util.Optional;

/** 查询和办理流程任务。执行办理时重新校验任务身份、当前状态和节点规则。 */
public interface ProcessTaskPort {
    /** 查询仍活动的引擎或本地加签任务，不存在/已完成返回 empty。 */
    Optional<ProcessTaskView> findActiveTask(String taskId);

    /** 读取待办办理人或候选人可见的任务、表单摘要及能力。实体数据通过业务引用另行授权读取。 */
    ProcessTaskDetail getTaskDetail(String taskId);

    /** 列出实例中全部可见活动任务，调用方不能假定只有一条。 */
    List<ProcessTaskView> listActiveTasks(String processInstanceId);

    /** 查询当前身份待办/已办或指定实例的活动任务。 */
    ProcessPage<ProcessTaskView> pageTasks(ProcessTaskQuery query);

    /** 查询操作可用性提示，命令执行时仍重新校验。 */
    TaskCapabilities getTaskCapabilities(String taskId);

    /** 认领调用方已授权的任务给当前执行身份；同一人已认领可返回未变化，他人已认领则冲突。 */
    TaskCommandResult claim(ClaimTaskCommand command);

    /**
     * 按已发布动作、表单和节点策略办理明确任务。平台负责普通/加签子任务/源任务分流，
     * 调用方无需预判任务种类，也不能跳过提交按钮和下一审批人校验。
     * @param command actionCode 决定业务动作；actionLabel 仅用于展示，不能改变通过/拒绝的含义
     * @return 实际任务结果和实例生命周期；登记延后完成不等于主流程已经推进
     */
    TaskCommandResult complete(CompleteTaskCommand command);

    /**
     * 跳过调用方已授权的活动待办，流程沿当前节点的已发布连线继续，条件使用实例当前变量。
     * 连线无法推进时，本次待办状态、日志与回执一并回滚。
     * @param command 本次待办的任务 ID、跳过原因及用于重试的请求 ID
     * @return 幂等回执，可用来核对实际处理的实例及任务
     * @throws ProcessOperationException 缺少审计操作人、任务状态已变化，或处于会签、加签、委托及回退重提等不支持的状态
     */
    ProcessCommandReceipt skip(SkipTaskCommand command);

    /**
     * 跳过调用方已授权的活动待办，后台调用可以直接指定审计归属。
     * @param operatorId 写入流程历史并隔离幂等回执的操作人标识
     */
    ProcessCommandReceipt skip(SkipTaskCommand command, String operatorId);

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

    /** 读操作。只返回当前实例实际经过且按当前任务的发布规则仍可回退的活动。 */
    List<HistoryReturnTarget> listHistoryReturnTargets(String taskId);

    /** 写操作。在原实例回退到指定历史活动；执行时重新鉴权、确认候选并写入同事务幂等回执。 */
    HistoryReturnResult returnToHistory(HistoryReturnCommand command);

    /** 读操作。使用与实际加签相同的任务权限和人员解析，返回解析问题及将生成的任务数。 */
    AddSignPreview previewAddSign(String taskId, AddSignType type, List<String> userIds);
}
