package com.workflow.entity.list.application;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort.Reference;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实体列表发布运行时解析服务，只允许使用已发布快照。
 *
 * <p>工具栏、行按钮、字段、场景等配置均从发布版本读取，保证草稿修改不会进入运行时。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityListPublishedRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final UiReleaseResolutionTokenService resolutionTokenService;
    private final JsonDocumentCodec codec;
    private final UiConfigReleaseMapper releaseMapper;
    private final ObjectMapper objectMapper;
    private final EmbedNativeListDependencySnapshotPort
            listDependencySnapshotPort;

    /**
     * 解析列表运行时配置，存在发布版本时用发布快照覆盖草稿。
     *
     * @param draft 草稿列表配置，为空返回 null
     * @return 运行时列表配置
     */
    public EntityListConfig resolveConfig(EntityListConfig draft) {
        return resolveConfig(draft, null, null, null);
    }

    /**
     * 按当前 ACTIVE 或父表单签名上下文中的固定版本解析列表。
     *
     * @param draft 草稿，作为 {@code releaseService.resolveRuntimeListRelease} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于解析配置时定位或关联目标
     * @param releaseVersion 发布版本，供本方法解析配置时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 解析后的配置结果，供调用方继续处理
     */
    public EntityListConfig resolveConfig(
            EntityListConfig draft,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
        if (draft == null) {
            return null;
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveRuntimeListRelease(
                        draft.getId(),
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken);
        EntityListConfig config = runtimeConfig(
                resolved.list(),
                resolved.releaseId(),
                resolved.releaseVersion(),
                resolved.pinned(),
                releaseResolutionToken);
        config.setViewCompositions(viewCompositions(resolved.snapshot()));
        return config;
    }

    /**
     * 使用已经验证的关联内容列表上下文读取精确钉定版本。
     *
     * <p>公开列表 API 不能凭 releaseId 读取历史版本；调用方必须先校验
     * {@code UiViewCompositionTokenService} 签名，再进入本服务。</p>
     *
     * @param draft 草稿，供本方法解析视图组合配置时使用
     * @param releaseId 发布版本ID，后续用于解析视图组合配置时定位或关联目标
     * @param releaseVersion 发布版本，供本方法解析视图组合配置时使用
     * @return 解析后的视图组合配置结果，供调用方继续处理
     */
    public EntityListConfig resolveViewCompositionConfig(
            EntityListConfig draft,
            String releaseId,
            Integer releaseVersion) {
        if (draft == null
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_REQUIRED",
                    "关联内容目标列表缺少固定发布版本");
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !"LIST".equals(release.getConfigType())
                || !draft.getId().equals(release.getConfigId())
                || !releaseVersion.equals(release.getVersion())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表固定版本不存在或不一致");
        }
        Map<String, Object> snapshot =
                releaseService.verifiedReleaseSnapshot(release);
        Object listDocument = snapshot.get("list");
        if (listDocument == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表发布快照缺少列表文档");
        }
        EntityListConfigDTO published = objectMapper.convertValue(
                listDocument,
                EntityListConfigDTO.class);
        if (!draft.getId().equals(published.getId())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表发布快照归属不一致");
        }
        EntityListConfig config = runtimeConfig(
                published,
                release.getId(),
                release.getVersion(),
                true,
                null);
        config.setViewCompositions(viewCompositions(snapshot));
        return config;
    }

    /**
     * 处理运行时配置，并将结果传给后续步骤。
     *
     * @param snapshot 快照，作为 {@code BeanUtils.copyProperties} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于处理运行时配置时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code config.setPublishedVersion} 的输入影响后续处理
     * @param pinned 固定，作为 {@code config.setPinnedRelease} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 处理后的运行时配置结果，供调用方继续处理
     */
    private EntityListConfig runtimeConfig(
            EntityListConfigDTO snapshot,
            String releaseId,
            Integer releaseVersion,
            boolean pinned,
            String releaseResolutionToken) {
        EntityListConfig config = new EntityListConfig();
        BeanUtils.copyProperties(snapshot, config);
        config.setToolbarConfig(write(snapshot.getToolbarConfig(), "发布工具栏配置"));
        config.setRowActionConfig(write(snapshot.getRowActionConfig(), "发布操作列配置"));
        config.setViewConfig(write(snapshot.getViewConfig(), "发布列表视图配置"));
        config.setSelectionConfig(write(snapshot.getSelectionConfig(), "发布选择配置"));
        config.setFixedFilterConfig(write(snapshot.getFixedFilterConfig(), "发布固定条件"));
        config.setActiveReleaseId(releaseId);
        config.setPublishedVersion(releaseVersion);
        config.setPublishedSnapshot(true);
        config.setRuntimeFields(snapshot.getFields() == null
                ? List.of() : List.copyOf(snapshot.getFields()));
        config.setPinnedRelease(pinned);
        config.setReleaseResolutionToken(
                releaseResolutionToken);
        log.info(
                "列表运行时解析完成: listId={}, listKey={}, releaseId={}, releaseVersion={}, source={}",
                LogValue.safe(config.getId()),
                LogValue.safe(config.getListKey()),
                LogValue.safe(releaseId),
                releaseVersion,
                pinned ? "PINNED" : "ACTIVE");
        return config;
    }

    /**
     * 关联内容只取自已完成哈希校验的发布快照，绝不从列表草稿补齐。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> viewCompositions(
            Map<String, Object> snapshot) {
        if (snapshot == null
                || !(snapshot.get("viewCompositions")
                instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> values = objectMapper.convertValue(
                snapshot.get("viewCompositions"),
                new TypeReference<List<Map<String, Object>>>() {});
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * 解析列表字段，发布快照存在时返回快照字段，否则回退到传入字段。
     *
     * @param config   列表配置
     * @param fallback 草稿字段回退列表
     * @return 运行时字段列表
     */
    public List<EntityListField> resolveFields(
            EntityListConfig config,
            List<EntityListField> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        return config.getRuntimeFields() == null
                ? List.of() : config.getRuntimeFields();
    }

    /**
     * 解析工具栏按钮，发布快照存在时返回快照工具栏，否则回退。
     *
     * @param config   列表配置
     * @param fallback 草稿工具栏回退列表
     * @return 运行时工具栏按钮列表
     */
    public List<Map<String, Object>> resolveToolbar(
            EntityListConfig config,
            List<Map<String, Object>> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        List<Map<String, Object>> buttons = readMapList(
                config.getToolbarConfig(),
                "发布工具栏配置");
        return authorizePinnedTargetForms(
                config.getId(),
                "TOOLBAR",
                buttons,
                config.getReleaseResolutionToken());
    }

    /**
     * 解析行内按钮，发布快照存在时返回快照行按钮，否则回退。
     *
     * @param config   列表配置
     * @param fallback 草稿行按钮回退列表
     * @return 运行时行按钮列表
     */
    public List<Map<String, Object>> resolveRowActions(
            EntityListConfig config,
            List<Map<String, Object>> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        List<Map<String, Object>> buttons = readMapList(
                config.getRowActionConfig(),
                "发布行按钮配置");
        return authorizePinnedTargetForms(
                config.getId(),
                "ROW_ACTION",
                buttons,
                config.getReleaseResolutionToken());
    }

    /**
     * 写入实体列表已发布运行时；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入实体列表已发布运行时的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于写入实体列表已发布运行时时匹配或展示
     * @return 写入后的实体列表已发布运行时文本，供调用方比较或展示
     */
    private String write(Object value, String label) {
        return value == null ? null : codec.write(value, label);
    }

    /**
     * 读取映射列表；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code codec.readArray} 的输入影响后续处理
     * @param label 标签，后续用于读取映射列表时匹配或展示
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMapList(
            String document,
            String label) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        return codec.readArray(document, label).stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .toList();
    }

    /**
     * 整理授权固定目标表单集合数据，供调用方遍历或继续处理。
     *
     * @param listId 列表ID，后续用于处理授权固定目标表单集合时定位或关联目标
     * @param buttonArea 按钮{@code area}，供本方法处理授权固定目标表单集合时使用
     * @param buttons 按钮集合，供本方法处理授权固定目标表单集合时使用
     * @param parentListResolutionToken 父级列表解析令牌，后续用于授权校验、关联或幂等去重
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> authorizePinnedTargetForms(
            String listId,
            String buttonArea,
            List<Map<String, Object>> buttons,
            String parentListResolutionToken) {
        if (buttons == null || buttons.isEmpty()) {
            return buttons == null ? List.of() : buttons;
        }
        List<Map<String, Object>> result =
                new ArrayList<>(buttons.size());
        int explicitFormCount = 0;
        int authorizedCount = 0;
        int incompleteCount = 0;
        Instant embedSessionExpiresAt = null;
        UiReleaseResolutionTokenService.EmbedListClaims embedClaims = null;
        if (resolutionTokenService.isEmbedListToken(
                parentListResolutionToken)) {
            embedClaims =
                    resolutionTokenService.verifyEmbedList(
                            parentListResolutionToken);
            embedSessionExpiresAt = Instant.ofEpochSecond(
                    embedClaims.expiresAt());
        }
        for (Map<String, Object> source : buttons) {
            Map<String, Object> button =
                    new java.util.LinkedHashMap<>(
                            source == null ? Map.of() : source);
            String formId = text(button.get("targetFormId"));
            String releaseId =
                    text(button.get("targetFormReleaseId"));
            Integer releaseVersion =
                    integer(button.get(
                            "targetFormReleaseVersion"));
            if (StringUtils.hasText(formId)) {
                explicitFormCount++;
            }
            if (!StringUtils.hasText(formId)
                    || !StringUtils.hasText(releaseId)
                    || releaseVersion == null) {
                if (StringUtils.hasText(formId)) {
                    incompleteCount++;
                }
            } else {
                // Embed LIST 中的显式按钮表单来自已校验的固定
                // List Release。派生表单令牌继承根 elr1 的绝对到期
                // 时间，既不会中途五分钟失效，也不能滚动延长 Session。
                String token = embedSessionExpiresAt == null
                        ? resolutionTokenService.issue(
                                UiRuntimeResolutionContext.standalone(),
                                formId,
                                releaseId,
                                releaseVersion,
                                0)
                        : resolutionTokenService.issue(
                                UiRuntimeResolutionContext.standalone(),
                                formId,
                                releaseId,
                                releaseVersion,
                                0,
                                embedSessionExpiresAt);
                if (StringUtils.hasText(token)) {
                    button.put(
                            "targetFormReleaseResolutionToken",
                            token);
                    authorizedCount++;
                }
            }
            authorizePinnedTargetList(
                    button,
                    embedClaims,
                    embedSessionExpiresAt);
            result.add(button);
        }
        if (explicitFormCount > 0) {
            log.info(
                    "列表显式表单按钮授权完成: listId={}, area={}, buttonCount={}, explicitFormCount={}, authorizedCount={}, incompleteCount={}",
                    LogValue.safe(listId),
                    LogValue.safe(buttonArea),
                    buttons.size(),
                    explicitFormCount,
                    authorizedCount,
                    incompleteCount);
        }
        return List.copyOf(result);
    }

    /**
     * 为固定 List Release 中的 open-list 目标派生同一 Session
     * 的签名列表令牌。派生坐标全部来自已发布快照，浏览器
     * 只能消费，不能把任意 entity/list/release 组合签名。
     *
     * @param button 按钮，作为 {@code text} 的输入影响后续处理
     * @param parentClaims 父级声明集合，作为 {@code requireDependencyClosure} 的输入影响后续处理
     * @param sessionExpiresAt 会话过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    private void authorizePinnedTargetList(
            Map<String, Object> button,
            UiReleaseResolutionTokenService.EmbedListClaims parentClaims,
            Instant sessionExpiresAt) {
        if (parentClaims == null
                || sessionExpiresAt == null
                || !"open-list".equalsIgnoreCase(
                        text(button.get("customMode")))) {
            return;
        }
        button.remove("targetListReleaseResolutionToken");
        button.remove("targetDefaultFormResolved");
        button.remove("targetDefaultFormId");
        button.remove("targetDefaultFormReleaseId");
        button.remove("targetDefaultFormReleaseVersion");
        button.remove("targetDefaultFormReleaseResolutionToken");
        String entityCode = text(button.get("targetEntityCode"));
        String listKey = text(button.get("targetListKey"));
        String listId = text(button.get("targetListId"));
        String releaseId = text(button.get("targetListReleaseId"));
        Integer releaseVersion = integer(
                button.get("targetListReleaseVersion"));
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)
                || !StringUtils.hasText(listId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || releaseVersion < 1) {
            throw new BusinessConflictException(
                    "LIST_TARGET_RELEASE_REQUIRED",
                    "open-list 按钮缺少固定的目标列表发布坐标");
        }
        EmbedNativeListDependencyClosure closure = requireDependencyClosure(
                parentClaims);
        ListNode parentNode = requireListNode(
                closure,
                new ListCoordinate(
                        parentClaims.entityCode(),
                        null,
                        parentClaims.listConfigId(),
                        parentClaims.releaseId(),
                        parentClaims.releaseVersion()),
                false);
        ListCoordinate target = parentNode.targets().stream()
                .filter(candidate -> Objects.equals(
                        entityCode, candidate.entityCode()))
                .filter(candidate -> Objects.equals(
                        listKey, candidate.listKey()))
                .filter(candidate -> Objects.equals(
                        listId, candidate.listConfigId()))
                .filter(candidate -> Objects.equals(
                        releaseId, candidate.listReleaseId()))
                .filter(candidate -> releaseVersion
                        == candidate.listReleaseVersion())
                .findFirst()
                .orElseThrow(() -> new BusinessForbiddenException(
                        "EMBED_LIST_DEPENDENCY_COORDINATE_MISMATCH",
                        "open-list 目标不属于当前 Session 固定依赖闭包"));
        String token = resolutionTokenService.issueEmbedList(
                target.entityCode(),
                target.listConfigId(),
                target.listReleaseId(),
                target.listReleaseVersion(),
                parentClaims.sessionId(),
                parentClaims.viewId(),
                parentClaims.viewReleaseId(),
                parentClaims.dependencyClosureVersion(),
                parentClaims.dependencyClosureHash(),
                sessionExpiresAt);
        if (!StringUtils.hasText(token)) {
            throw new BusinessConflictException(
                    "LIST_TARGET_RELEASE_TOKEN_FAILED",
                    "open-list 目标列表固定令牌签发失败");
        }
        button.put("targetListReleaseResolutionToken", token);

        // 默认表单只能来自 Launch-time closure。这里不再读取 ACTIVE；
        // resolved=true + null 是权威“没有新增表单”，前端必须禁用回退。
        ListNode targetNode = requireListNode(closure, target, true);
        button.put(
                "targetDefaultFormResolved",
                targetNode.defaultFormResolved());
        FormCoordinate defaultForm = targetNode.defaultForm();
        if (defaultForm == null) {
            return;
        }
        String formToken = resolutionTokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                defaultForm.formId(),
                defaultForm.formReleaseId(),
                defaultForm.formReleaseVersion(),
                0,
                sessionExpiresAt);
        if (!StringUtils.hasText(formToken)) {
            throw new BusinessConflictException(
                    "LIST_TARGET_DEFAULT_FORM_TOKEN_FAILED",
                    "open-list 目标默认表单固定令牌签发失败");
        }
        button.put("targetDefaultFormId", defaultForm.formId());
        button.put(
                "targetDefaultFormReleaseId",
                defaultForm.formReleaseId());
        button.put(
                "targetDefaultFormReleaseVersion",
                defaultForm.formReleaseVersion());
        button.put(
                "targetDefaultFormReleaseResolutionToken",
                formToken);
    }

    /**
     * 按 elr1 的短引用从 immutable View Release 加载并校验闭包。
     *
     * @param claims 声明集合，作为 {@code listDependencySnapshotPort.read} 的输入影响后续处理
     * @return 校验并获取后的依赖闭包结果，供调用方继续处理
     */
    private EmbedNativeListDependencyClosure requireDependencyClosure(
            UiReleaseResolutionTokenService.EmbedListClaims claims) {
        if (listDependencySnapshotPort == null
                || !StringUtils.hasText(claims.viewId())
                || claims.dependencyClosureVersion()
                != EmbedNativeListDependencyClosure.CURRENT_VERSION
                || !StringUtils.hasText(
                claims.dependencyClosureHash())) {
            throw new BusinessForbiddenException(
                    "EMBED_LIST_DEPENDENCY_CLOSURE_REQUIRED",
                    "当前 Embed 列表令牌缺少依赖闭包引用");
        }
        try {
            return listDependencySnapshotPort.read(
                    new Reference(
                            claims.sessionId(),
                            claims.viewId(),
                            claims.viewReleaseId(),
                            claims.dependencyClosureVersion(),
                            claims.dependencyClosureHash()));
        } catch (RuntimeException error) {
            throw new BusinessForbiddenException(
                    "EMBED_LIST_DEPENDENCY_CLOSURE_MISMATCH",
                    "当前 Embed 列表依赖闭包无效");
        }
    }

    /**
     * 校验并获取列表节点；不满足约束时阻止后续处理。
     *
     * @param closure 闭包，供本方法校验并获取列表节点时使用
     * @param coordinate 坐标，作为 {@code filter} 的输入影响后续处理
     * @param requireExactListKey {@code require}精确列表键，后续用于授权校验、关联或幂等去重
     * @return 校验并获取后的列表节点结果，供调用方继续处理
     */
    private static ListNode requireListNode(
            EmbedNativeListDependencyClosure closure,
            ListCoordinate coordinate,
            boolean requireExactListKey) {
        return closure.nodes().stream()
                .filter(node -> node != null && node.list() != null)
                .filter(node -> Objects.equals(
                        coordinate.entityCode(),
                        node.list().entityCode()))
                .filter(node -> !requireExactListKey
                        || Objects.equals(
                        coordinate.listKey(), node.list().listKey()))
                .filter(node -> Objects.equals(
                        coordinate.listConfigId(),
                        node.list().listConfigId()))
                .filter(node -> Objects.equals(
                        coordinate.listReleaseId(),
                        node.list().listReleaseId()))
                .filter(node -> coordinate.listReleaseVersion()
                        == node.list().listReleaseVersion())
                .findFirst()
                .orElseThrow(() -> new BusinessForbiddenException(
                        "EMBED_LIST_DEPENDENCY_NODE_MISSING",
                        "当前列表不属于 Embed Session 固定依赖闭包"));
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
