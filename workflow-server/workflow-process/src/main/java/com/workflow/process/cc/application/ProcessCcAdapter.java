package com.workflow.process.cc.application;

import com.workflow.contracts.process.instance.model.ProcessRuntimeViews.CcItem;
import com.workflow.contracts.process.instance.port.ProcessCcPort;
import com.workflow.contracts.process.model.ProcessPage;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.command.application.ProcessInvocationAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 知会收件人与平台原有用户名坐标一致，业务调用只操作本人记录。 */
@Component
@RequiredArgsConstructor
public class ProcessCcAdapter implements ProcessCcPort {
    private final ProcessCcService cc;
    private final ProcessInvocationAccess callers;

    @Override
    public ProcessPage<CcItem> pageReceived(int pageNumber, int pageSize, String keyword, String starterName,
            LocalDateTime from, LocalDateTime toExclusive) {
        var page = cc.getUserCcPage(recipient(), pageNumber, pageSize, keyword, starterName, from, toExclusive);
        return new ProcessPage<>(page.getRecords().stream().map(row -> new CcItem(row.getId(), row.getProcessInstanceId(),
                row.getProcessName(), row.getNodeName(), row.getDataName(), row.getComment(), row.getReadStatus(),
                row.getCreateTime(), row.getReadTime())).toList(), page.getTotal(), (int) page.getPageNum(), (int) page.getPageSize());
    }

    @Override public void markRead(String ccId) { cc.markAsRead(ccId, recipient()); }
    @Override public void markAllRead() { cc.markAllAsRead(recipient()); }

    private String recipient() {
        String username = callers.requireActor().username();
        if (!StringUtils.hasText(username)) throw new ForbiddenException("知会收件需要当前用户名");
        return username;
    }
}
