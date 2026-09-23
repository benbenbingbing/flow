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

    /**
     * 规范化精确来源策略全部；输出作为后续校验或处理的输入。
     *
     * @param origins 来源，供本方法规范化精确来源策略全部时使用
     * @return 精确来源策略集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化精确来源策略的原始输入，结果供调用方继续使用
     * @return 规范化后的精确来源策略文本，供调用方比较或展示
     */
    public String normalize(String value) {
        try {
            // Grant 写入和 Launch 比对必须逐字节使用同一 canonical 规则；若各自维护 URI
            // 解析器，端口、IDN、IPv6 或根路径的细微差异会形成“可配置但永远无法启动”的授权。
            return EmbedOriginNormalizer.normalize(value);
        } catch (EmbedException exception) {
            throw invalid();
        }
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "allowedOrigins 只接受不含路径、Query、Fragment 或通配符的 HTTPS Origin");
    }

}
