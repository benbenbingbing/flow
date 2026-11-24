package com.workflow.process.cc.application;

import com.workflow.port.process.model.instance.ProcessRuntimeViews.CcItem;
import com.workflow.port.process.ProcessCcRecordPort;
import com.workflow.port.process.model.ProcessPage;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.command.application.ProcessInvocationAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 知会按接收时的用户名定位。后台可直接传入调用方已授权的收件人。 */
@Component
@RequiredArgsConstructor
public class ProcessCcAdapter implements ProcessCcRecordPort {
    private final ProcessCcService cc;
    private final ProcessInvocationAccess callers;

    @Override
    public ProcessPage<CcItem> pageReceived(int pageNumber, int pageSize, String keyword, String starterName,
            LocalDateTime from, LocalDateTime toExclusive) {
        return pageReceived(recipient(), pageNumber, pageSize, keyword, starterName, from, toExclusive);
    }

    @Override
    public ProcessPage<CcItem> pageReceived(String recipientUsername, int pageNumber, int pageSize,
            String keyword, String starterName, LocalDateTime from, LocalDateTime toExclusive) {
        var page = cc.getUserCcPage(requireRecipient(recipientUsername), pageNumber, pageSize, keyword, starterName, from, toExclusive);
        return new ProcessPage<>(page.getRecords().stream().map(row -> new CcItem(row.getId(), row.getProcessInstanceId(),
                row.getProcessName(), row.getNodeName(), row.getDataName(), row.getComment(), row.getReadStatus(),
                row.getCreateTime(), row.getReadTime())).toList(), page.getTotal(), (int) page.getPageNum(), (int) page.getPageSize());
    }

    @Override public void markRead(String ccId) { markRead(ccId, recipient()); }

    @Override public void markRead(String ccId, String recipientUsername) {
        cc.markAsRead(ccId, requireRecipient(recipientUsername));
    }

    @Override public void markAllRead() { markAllRead(recipient()); }

    @Override public void markAllRead(String recipientUsername) {
        cc.markAllAsRead(requireRecipient(recipientUsername));
    }

    private String requireRecipient(String username) {
        return com.workflow.port.process.model.ProcessValues.required(username, "recipientUsername");
    }

    private String recipient() {
        String username = callers.requireActor().username();
        if (!StringUtils.hasText(username)) throw new ForbiddenException("知会收件需要当前用户名");
        return username;
    }
}
