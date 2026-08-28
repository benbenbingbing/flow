package com.workflow.embed.management.security;

import com.workflow.embed.application.validation.EmbedOriginNormalizer;
import com.workflow.embed.domain.EmbedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/** 解析并规范化精确父页面 Origin，拒绝路径、通配符和非 HTTPS 来源。 */
@Component
public class ExactOriginPolicy {

    public List<String> normalizeAll(List<String> origins) {
        if (origins == null || origins.isEmpty() || origins.size() > 20) {
            throw new IllegalArgumentException("allowedOrigins 必须包含 1 到 20 个精确 Origin");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        origins.forEach(origin -> normalized.add(normalize(origin)));
        if (normalized.size() > 20) {
            throw new IllegalArgumentException("allowedOrigins 最多允许 20 个精确 Origin");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    public String normalize(String value) {
        try {
            // Grant 写入和 Launch 比对必须逐字节使用同一 canonical 规则；若各自维护 URI
            // 解析器，端口、IDN、IPv6 或根路径的细微差异会形成“可配置但永远无法启动”的授权。
            return EmbedOriginNormalizer.normalize(value);
        } catch (EmbedException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "allowedOrigins 只接受不含路径、Query、Fragment 或通配符的 HTTPS Origin");
    }

}
