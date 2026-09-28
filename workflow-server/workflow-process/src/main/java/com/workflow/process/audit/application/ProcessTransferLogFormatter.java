package com.workflow.process.audit.application;

import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;

/** 流程进度和审批历史共用转办描述，避免管理员交接的来源与原因在展示时丢失。 */
public final class ProcessTransferLogFormatter {
    private static final String HANDOVER_PREFIX = "人员交接：";

    private ProcessTransferLogFormatter() { }

    /**
     * 为 TRANSFER 日志生成历史说明；人员交接展示来源、接收人和原因，普通转办保持原展示规则。
     *
     * @param log 已读取的非空操作日志；oldValue/newValue 使用交接时保存的姓名快照，避免人员更名后改写历史
     * @return 历史说明；普通转办既无目标也无说明时仍返回 null
     */
    public static String comment(ProcessOperationLog log) {
        String comment = log.getOperationComment();
        if (comment != null && comment.startsWith(HANDOVER_PREFIX)) {
            String reason = comment.substring(HANDOVER_PREFIX.length()).trim();
            String transfer = HANDOVER_PREFIX + displayIdentity(log.getOldValue()) + " → " + displayIdentity(log.getNewValue());
            return reason.isEmpty() ? transfer : transfer + "；原因：" + reason;
        }
        return log.getNewValue() != null ? "转办给: " + log.getNewValue() : comment;
    }

    /** 兼容缺少人员快照的旧日志，不将 null 当作姓名展示。 */
    private static String displayIdentity(String value) {
        return value == null || value.isBlank() ? "未记录人员" : value;
    }
}
