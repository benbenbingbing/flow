package com.workflow.process.audit.application;

import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 历史说明必须区分管理员人员交接与普通转办，并完整保留交接责任链和原因。 */
class ProcessTransferLogFormatterTest {
    @Test
    void handoverDisplaysSourceTargetAndReasonFromStoredSnapshots() {
        var log = log("张三(zhangsan)", "李四(lisi)", "人员交接：离职交接，继续处理财务审核");
        assertEquals("人员交接：张三(zhangsan) → 李四(lisi)；原因：离职交接，继续处理财务审核",
                ProcessTransferLogFormatter.comment(log));
    }

    @Test
    void ordinaryTransferKeepsExistingTargetOrCommentFallback() {
        assertEquals("转办给: lisi", ProcessTransferLogFormatter.comment(log("zhangsan", "lisi", "临时协助")));
        assertEquals("临时协助", ProcessTransferLogFormatter.comment(log("zhangsan", null, "临时协助")));
        assertNull(ProcessTransferLogFormatter.comment(log("zhangsan", null, null)));
        assertEquals("转办给: lisi", ProcessTransferLogFormatter.comment(log("zhangsan", "lisi", "补充说明，人员交接：不属于交接记录")));
    }

    @Test
    void incompleteHistoricalHandoverDoesNotDisplayNullOrInventAReason() {
        assertEquals("人员交接：未记录人员 → lisi；原因：岗位调整",
                ProcessTransferLogFormatter.comment(log(null, "lisi", "人员交接： 岗位调整 ")));
        assertEquals("人员交接：zhangsan → 未记录人员",
                ProcessTransferLogFormatter.comment(log("zhangsan", " ", "人员交接：")));
    }

    private ProcessOperationLog log(String source, String target, String comment) {
        var log = new ProcessOperationLog();
        log.setOperationType("TRANSFER");
        log.setOldValue(source);
        log.setNewValue(target);
        log.setOperationComment(comment);
        return log;
    }
}
