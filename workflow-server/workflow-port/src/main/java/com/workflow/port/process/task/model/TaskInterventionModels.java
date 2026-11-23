package com.workflow.port.process.task.model;

import com.workflow.port.process.model.CommandMeta;
import com.workflow.port.process.model.ProcessValues;

import java.util.List;
import java.util.Objects;

/** 历史回退与加签预检使用的独立业务投影。 */
public final class TaskInterventionModels {
    private TaskInterventionModels() { }

    /** activityInstanceId 精确标识本轮的一次历史活动，可作为回退命令目标。 */
    public record HistoryReturnTarget(String activityInstanceId, String nodeId, String nodeName, boolean resubmit) { }

    /** meta 供重试重放，formReference 可用于核对当前任务绑定的发布表单。 */
    public record HistoryReturnCommand(CommandMeta meta, String taskId, String targetActivityInstanceId,
            String comment, TaskFormReference formReference) {
        public HistoryReturnCommand {
            Objects.requireNonNull(meta, "meta");
            taskId = ProcessValues.required(taskId, "taskId");
            targetActivityInstanceId = ProcessValues.required(targetActivityInstanceId, "targetActivityInstanceId");
            comment = ProcessValues.required(comment, "comment");
            if (targetActivityInstanceId.length() > 128 || comment.length() > 2000) {
                throw new IllegalArgumentException("历史回退目标或意见超过长度限制");
            }
        }
    }

    /** 返回实际回退到的节点，requestId 可用于关联操作历史。 */
    public record HistoryReturnResult(String processInstanceId, String targetNodeId, String requestId) { }

    /** 预检用户仅含选择确认所需的身份字段。 */
    public record AddSignUser(String id, String username, String displayName) { }

    /** 四组列表供业务确认人员和修正输入；taskCount 是有效人员将产生的任务数。 */
    public record AddSignPreview(AddSignType type, List<AddSignUser> users, List<String> duplicates,
            List<String> disabled, List<String> invalid, int taskCount, String structure) {
        public AddSignPreview {
            users = List.copyOf(users);
            duplicates = List.copyOf(duplicates);
            disabled = List.copyOf(disabled);
            invalid = List.copyOf(invalid);
        }
    }
}
