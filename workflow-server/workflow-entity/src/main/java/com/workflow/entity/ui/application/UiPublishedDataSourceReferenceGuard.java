package com.workflow.entity.ui.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 阻止删除仍可能被发布运行时调用的接口服务。
 *
 * <p>检查范围包含可被签名上下文或 Embed 固定的历史 FORM/LIST
 * 发布版本。该检查是全局完整性约束，不按操作者权限过滤；否则管理员
 * 可能删除自己看不到、但运行时仍在使用的服务。</p>
 */
@Service
@RequiredArgsConstructor
public class UiPublishedDataSourceReferenceGuard {

    private static final Set<String> REFERENCE_KEYS = Set.of(
            "serviceId", "dataSourceId", "queryDataSourceId");
    private static final int MAX_EMBEDDED_JSON_DEPTH = 4;

    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;

    /**
     * 校验接口服务未被任一仍可执行的发布版本引用。
     *
     * @param serviceId 待删除接口服务 ID
     * @throws BusinessConflictException 存在线上引用，或候选快照无法可靠校验
     */
    public void requireNoExecutableReferences(String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            throw new IllegalArgumentException("接口服务ID不能为空");
        }
        String normalizedId = serviceId.trim();
        for (UiConfigRelease release : safe(
                releaseMapper.findExecutableDataSourceReferenceCandidates(
                        normalizedId))) {
            if (release == null) {
                continue;
            }
            String path;
            try {
                Map<String, Object> snapshot =
                        releaseService.verifiedReleaseSnapshot(release);
                path = referencePath(
                        snapshot, normalizedId, "$", 0);
            } catch (IllegalArgumentException exception) {
                throw unverifiable("FORM/LIST 发布版本");
            }
            if (path != null) {
                throw new BusinessConflictException(
                        "UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                        "接口服务仍被可执行的发布版本引用，不能安全删除："
                                + release.getConfigType() + "/"
                                + release.getConfigId() + "@v"
                                + release.getVersion() + " " + path);
            }
        }
    }

    private String referencePath(
            Object value,
            String serviceId,
            String path,
            int embeddedDepth) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (REFERENCE_KEYS.contains(key)
                        && Objects.equals(
                                serviceId,
                                text(entry.getValue()))) {
                    return path + "." + key;
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String found = referencePath(
                        entry.getValue(),
                        serviceId,
                        path + "." + entry.getKey(),
                        embeddedDepth);
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                String found = referencePath(
                        list.get(index),
                        serviceId,
                        path + "[" + index + "]",
                        embeddedDepth);
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof String document
                && document.contains(serviceId)) {
            String trimmed = document.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                if (embeddedDepth >= MAX_EMBEDDED_JSON_DEPTH) {
                    throw new IllegalArgumentException(
                            "嵌套发布配置超过安全解析层级");
                }
                Object nested = codec.read(
                        trimmed, "嵌套发布配置");
                return referencePath(
                        nested,
                        serviceId,
                        path + "<json>",
                        embeddedDepth + 1);
            }
        }
        return null;
    }

    private BusinessConflictException unverifiable(String source) {
        return new BusinessConflictException(
                "UI_DATA_SOURCE_PUBLISHED_REFERENCE_UNVERIFIABLE",
                "发现可能引用该接口服务的" + source
                        + "，但文档无法可靠校验；请先修复发布版本后再删除");
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
