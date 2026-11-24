package com.workflow.port.process;

import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.model.query.*;

/** 预览下一个审批节点并筛选可选办理人。 */
public interface ProcessNextApprovalPort {
    /** 无副作用地预览下一审批节点，无法提前解析时明确返回 DEFERRED。 */
    NextApprovalPreview previewNextApproval(NextApprovalQuery query);

    /** 根据同一预览输入与作用域查询可选审批人。 */
    ProcessPage<ApproverOption> pageNextApproverOptions(NextApproverOptionsQuery query);
}
