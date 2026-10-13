package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 单次运行请求的配置上下文。只缓存已经受理的当前配置，不跨请求缓存；
 * 事务终检复用它，避免事件执行后因另一人发布而切换规则或重复执行副作用。
 */
@Component
@RequiredArgsConstructor
public class CurrentFormRuntimeContext {
    private static final String CACHE_KEY = CurrentFormRuntimeContext.class.getName();
    private final UiConfigReleaseMapper mapper;
    private final ObjectMapper json;

    /** 按稳定表单 ID 读取本请求采用的配置；未发布表单不能回退到草稿。 */
    public UiConfigRelease current(String formId) {
        Map<String, UiConfigRelease> cache = cache();
        UiConfigRelease row = cache.get(formId);
        if (row == null) {
            row = mapper.findActive("FORM", formId);
            if (row == null) throw new BusinessConflictException("FORM_NOT_PUBLISHED", "表单尚未发布: " + formId);
            cache.put(formId, row);
        }
        return row;
    }

    /**
     * 列表同样固定本请求首次受理的已校验配置。loader 同时检查 ACTIVE 指针和归属，
     * 后续事件及变更复用结果，避免副作用发生后因另一人发布而切换规则。
     */
    public UiConfigRelease currentList(String listId, java.util.function.Supplier<UiConfigRelease> loader) {
        Map<String, UiConfigRelease> cache = cache();
        return cache.computeIfAbsent("LIST:" + listId, ignored -> loader.get());
    }

    /** 事务终检只能复用本次已接受的指纹，绝不能查询旧历史记录。 */
    public UiConfigRelease accepted(String formId, String hash) {
        UiConfigRelease row = current(formId);
        if (!java.util.Objects.equals(hash, row.getId())) throw changed();
        return row;
    }

    /** 摘要覆盖根表单与所有子表单，单独发布子表单也会使旧页面摘要发生变化。 */
    public String graphHash(String formId) {
        Map<String, String> fingerprints = new TreeMap<>();
        collect(formId, new LinkedHashSet<>(), fingerprints);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(fingerprints)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算表单依赖摘要", exception);
        }
    }

    private void collect(String formId, Set<String> path, Map<String, String> fingerprints) {
        if (path.size() >= 8 || !path.add(formId)) throw new IllegalStateException("当前表单引用循环或超过 8 层: " + formId);
        UiConfigRelease row = current(formId);
        fingerprints.put(formId, row.getContentHash());
        try {
            Set<String> children = new LinkedHashSet<>();
            references(json.readValue(row.getSnapshotDocument(), Object.class), children);
            for (String child : children) collect(child, path, fingerprints);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("当前表单发布文档无效: " + formId, exception);
        } finally { path.remove(formId); }
    }

    private void references(Object value, Set<String> children) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (value instanceof Map<?, ?> map) {
            // 仅平台子表单身份参与依赖图，业务参数里的普通 id 不构成配置引用。
            for (String key : java.util.List.of("childFormId", "refFormId", "publishedFormId")) {
                Object id = map.get(key);
                if (id instanceof String text && !text.isBlank()) children.add(text);
            }
            for (var entry : map.entrySet()) {
                // legacyProps 仅供设计兼容追溯，不构成当前可执行依赖。
                if (Set.of("legacyProps", "legacyPropsDocument").contains(String.valueOf(entry.getKey()))) continue;
                Object child = entry.getValue();
                String name = String.valueOf(entry.getKey());
                if (child instanceof String text && (name.endsWith("Document") || "componentProps".equals(name))
                        && (text.trim().startsWith("{") || text.trim().startsWith("[")))
                    references(json.readValue(text, Object.class), children);
                else references(child, children);
            }
        } else if (value instanceof Iterable<?> list) for (Object child : list) references(child, children);
    }

    @SuppressWarnings("unchecked")
    private Map<String, UiConfigRelease> cache() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            Object existing = request.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (existing != null) return (Map<String, UiConfigRelease>) existing;
            Map<String, UiConfigRelease> result = new LinkedHashMap<>();
            request.setAttribute(CACHE_KEY, result, RequestAttributes.SCOPE_REQUEST);
            return result;
        }
        // 非 HTTP 的业务调用以实际事务为受理边界，并在结束后释放线程资源。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            Object existing = TransactionSynchronizationManager.getResource(CACHE_KEY);
            if (existing != null) return (Map<String, UiConfigRelease>) existing;
            Map<String, UiConfigRelease> result = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(CACHE_KEY, result);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(CACHE_KEY); }
            });
            return result;
        }
        return new LinkedHashMap<>();
    }

    static BusinessConflictException changed() {
        return new BusinessConflictException("FORM_CONFIGURATION_CHANGED", "表单配置已更新，填写内容已保留，请重新加载后核对");
    }
}
