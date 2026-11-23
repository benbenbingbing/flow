package com.workflow.port.process;

import com.workflow.port.process.instance.model.ProcessRuntimeViews.CcItem;
import com.workflow.port.process.model.ProcessPage;

import java.time.LocalDateTime;

/** 读取及维护当前调用人的知会收件箱。 */
public interface ProcessCcRecordPort {
    /** 读操作。同一身份下筛选并分页，toExclusive 是可选的排他截止时间。 */
    ProcessPage<CcItem> pageReceived(int pageNumber, int pageSize, String keyword, String starterName,
            LocalDateTime from, LocalDateTime toExclusive);

    /** 写操作。将当前收件人名下的所选知会标记为已读；已读请求可重复提交。 */
    void markRead(String ccId);

    /** 写操作。将当前收件人所有知会标记为已读。 */
    void markAllRead();
}
