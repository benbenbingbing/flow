package com.workflow.process.command.application;

import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.process.task.api.request.TaskCompleteRequest;
import java.util.Map;
import java.util.UUID;

/** 旧 HTTP 协议到公共命令的无状态转换；不包含授权或流程推进逻辑。 */
public final class ProcessHttpCommands {
    private ProcessHttpCommands() { }
    /** 客户端提供的意图键原样保留；旧客户端每次请求仍视为新的业务意图。 */
    public static CommandMeta meta(String requestId) {
        return new CommandMeta(requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId);
    }
    /** 保留旧接口的表单/记录声明，供事务内的联合校验使用。 */
    @SuppressWarnings("unchecked")
    public static TaskCompleteRequest task(String taskId, Map<String, Object> values) {
        Map<String, Object> source = values == null ? Map.of() : values;
        var request = new TaskCompleteRequest();
        request.setTaskId(taskId);
        request.setRequestId(text(source.get("requestId")));
        request.setAction(text(source.get("action")));
        request.setComment(text(source.get("comment")));
        request.setTransferTo(text(source.get("transferTo")));
        request.setActionLabel(text(source.get("actionLabel")));
        request.setFormData((Map<String, Object>) source.get("formData"));
        request.setFormId(text(source.get("formId")));
        request.setFormConfigHash(text(source.get("formConfigHash")));
        request.setEntityCode(text(source.get("entityCode")));
        request.setRecordId(text(source.get("recordId")));
        request.setListKey(text(source.get("listKey")));
        return request;
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
}
