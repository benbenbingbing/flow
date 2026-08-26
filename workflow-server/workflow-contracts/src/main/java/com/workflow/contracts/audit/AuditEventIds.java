package com.workflow.contracts.audit;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 为来自权威业务日志的投影事件生成稳定且满足数据库长度约束的 ID。
 */
public final class AuditEventIds {

    private AuditEventIds() {
    }

    public static String stable(
            String namespace,
            Object... parts) {
        StringJoiner value = new StringJoiner("|");
        value.add(String.valueOf(namespace));
        if (parts != null) {
            for (Object part : parts) {
                value.add(String.valueOf(part));
            }
        }
        return UUID.nameUUIDFromBytes(
                        value.toString().getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }
}
