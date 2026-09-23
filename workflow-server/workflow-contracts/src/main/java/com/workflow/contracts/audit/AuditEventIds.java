package com.workflow.contracts.audit;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 为来自权威业务日志的投影事件生成稳定且满足数据库长度约束的 ID。
 */
public final class AuditEventIds {

    /**
     * 初始化审计事件ID 集合，保存构造参数供后续方法使用。
     */
    private AuditEventIds() {
    }

    /**
     * 生成稳定文本，供后续匹配或展示。
     *
     * @param namespace 命名空间，作为 {@code value.add} 的输入影响后续处理
     * @param parts {@code parts}，供本方法处理稳定时使用
     * @return 处理后的稳定文本，供调用方比较或展示
     */
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
