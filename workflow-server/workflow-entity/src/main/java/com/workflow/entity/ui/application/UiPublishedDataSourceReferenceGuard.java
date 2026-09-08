package com.workflow.entity.ui.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyReleaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyRelease;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 阻止删除仍可能被发布运行时调用的接口服务。
 *
 * <p>检查范围包含可被签名上下文或 Embed 固定的历史 FORM/LIST 发布版本，
 * 以及当前生效的新旧实体变更策略。该检查是全局完整性约束，不按操作者
 * 权限过滤；否则管理员可能删除自己看不到、但运行时仍在使用的服务。</p>
 */
@Service
@RequiredArgsConstructor
public class UiPublishedDataSourceReferenceGuard {

    private static final Set<String> REFERENCE_KEYS = Set.of(
            "serviceId", "dataSourceId", "queryDataSourceId");
    private static final int MAX_EMBEDDED_JSON_DEPTH = 4;

    private final UiConfigReleaseMapper releaseMapper;
    private final EntityMutationPolicyReleaseMapper mutationReleaseMapper;
    private final EntityVersionConfigReleaseMapper legacyMutationReleaseMapper;
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

        for (EntityMutationPolicyRelease release : safe(
                mutationReleaseMapper
                        .findActiveDataSourceReferenceCandidates(
                                normalizedId))) {
            if (release == null) {
                continue;
            }
            String path;
            try {
                Map<String, Object> document = codec.readObject(
                        release.getConfigDocument(), "实体变更策略发布文档");
                path = rootPolicyEnabled(document)
                        ? managedInterfaceReferencePath(
                                document, normalizedId, "$", 0)
                        : null;
            } catch (IllegalArgumentException exception) {
                throw unverifiable("实体变更策略发布版本");
            }
            if (path != null) {
                throw new BusinessConflictException(
                        "UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                        "接口服务仍被当前生效实体变更策略引用，不能安全删除："
                                + release.getConfigId() + "@v"
                                + release.getVersion() + " " + path);
            }
        }

        // 未迁移实体仍从旧版 active release 执行 MANAGED_INTERFACE；即使
        // 新版策略表没有记录，也不能遗漏这条兼容运行路径。反之，同实体
        // 的原生 active release 真实存在时运行时不会再 fallback legacy。
        for (EntityVersionConfigRelease release : safe(
                legacyMutationReleaseMapper
                        .findActiveDataSourceReferenceCandidates(
                                normalizedId))) {
            if (release == null) {
                continue;
            }
            if (legacyMutationReleaseMapper
                    .countActiveNativePolicyForLegacyConfig(
                            release.getConfigId()) > 0) {
                continue;
            }
            String path;
            try {
                Map<String, Object> document = codec.readObject(
                        release.getConfigDocument(), "旧版实体变更配置发布文档");
                path = rootPolicyEnabled(document)
                        ? managedInterfaceReferencePath(
                                document, normalizedId, "$", 0)
                        : null;
            } catch (IllegalArgumentException exception) {
                throw unverifiable("旧版实体变更配置发布版本");
            }
            if (path != null) {
                throw new BusinessConflictException(
                        "UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                        "接口服务仍被当前生效旧版实体变更配置引用，不能安全删除："
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

    private String managedInterfaceReferencePath(
            Object value,
            String serviceId,
            String path,
            int embeddedDepth) {
        if (value instanceof Map<?, ?> map) {
            // 根配置只有 enabled=true 才执行；步骤则默认启用，只有显式
            // enabled=false 才跳过。这里仅处理步骤语义，根语义由入口判断。
            if (map.containsKey("stepType")
                    && explicitlyDisabled(map.get("enabled"))) {
                return null;
            }
            if ("MANAGED_INTERFACE".equals(normalize(
                    text(map.get("stepType"))))) {
                if (Objects.equals(
                        serviceId, text(map.get("providerCode")))) {
                    return path + ".providerCode";
                }
                String configPath = referencePath(
                        map.get("config"),
                        serviceId,
                        path + ".config",
                        embeddedDepth);
                if (configPath != null) {
                    return configPath;
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String found = managedInterfaceReferencePath(
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
                String found = managedInterfaceReferencePath(
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
                            "嵌套实体变更策略超过安全解析层级");
                }
                return managedInterfaceReferencePath(
                        codec.read(trimmed, "嵌套实体变更策略"),
                        serviceId,
                        path + "<json>",
                        embeddedDepth + 1);
            }
        }
        return null;
    }

    private boolean rootPolicyEnabled(Map<String, Object> document) {
        Object enabled = document.get("enabled");
        return Boolean.TRUE.equals(enabled)
                || "true".equalsIgnoreCase(text(enabled));
    }

    private boolean explicitlyDisabled(Object enabled) {
        return Boolean.FALSE.equals(enabled)
                || "false".equalsIgnoreCase(text(enabled));
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

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
