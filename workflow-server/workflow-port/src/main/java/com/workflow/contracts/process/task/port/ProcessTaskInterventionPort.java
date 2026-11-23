package com.workflow.contracts.process.task.port;

import com.workflow.contracts.process.task.model.AddSignType;
import com.workflow.contracts.process.task.model.TaskInterventionModels.AddSignPreview;
import com.workflow.contracts.process.task.model.TaskInterventionModels.HistoryReturnCommand;
import com.workflow.contracts.process.task.model.TaskInterventionModels.HistoryReturnResult;
import com.workflow.contracts.process.task.model.TaskInterventionModels.HistoryReturnTarget;

import java.util.List;

/** 查询并执行发布版本允许的任务干预；由宿主使用当前办理身份授权。 */
public interface ProcessTaskInterventionPort {
    /** 只返回当前实例实际经过且按当前任务的发布规则仍可回退的活动。 */
    List<HistoryReturnTarget> listHistoryReturnTargets(String taskId);

    /** 在原实例回退到指定历史活动；执行时重新鉴权、确认候选并写入同事务幂等回执。 */
    HistoryReturnResult returnToHistory(HistoryReturnCommand command);

    /** 使用与实际加签相同的任务权限和人员解析，返回解析问题及将生成的任务数。 */
    AddSignPreview previewAddSign(String taskId, AddSignType type, List<String> userIds);
}
