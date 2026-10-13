package com.workflow.entity.list.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.port.EmbedNativeListRuntimePort;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 校验 Embed 固定列表及服务端依赖闭包。
 *
 * <p>适配器不解析列、组件、按钮或数据源；iframe 后续直接调用
 * Flow 标准列表 schema/query 接口。</p>
 */
@Component
public class EntityEmbedNativeListRuntimeAdapter
        implements EmbedNativeListRuntimePort {

    private final EntityListConfigMapper listConfigMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final UiConfigReleaseService releaseService;
    private final ObjectProvider<EmbedNativeListDependencySnapshotPort> dependencySnapshots;
    private final ObjectMapper objectMapper;

    /**
     * 初始化实体嵌入式原生列表运行时适配器，保存构造参数供后续方法使用。
     *
     * @param listConfigMapper 列表配置映射器依赖，保存到当前对象供后续业务方法调用
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     * @param dependencySnapshots 按已认证会话读取固定列表闭包
     * @param objectMapper 解析已发布按钮配置
     */
    public EntityEmbedNativeListRuntimeAdapter(
            EntityListConfigMapper listConfigMapper,
            EntityDefinitionMapper definitionMapper,
            UiConfigReleaseService releaseService,
            ObjectProvider<EmbedNativeListDependencySnapshotPort> dependencySnapshots, ObjectMapper objectMapper) {
        this.listConfigMapper = listConfigMapper;
        this.definitionMapper = definitionMapper;
        this.releaseService = releaseService;
        this.dependencySnapshots = dependencySnapshots;
        this.objectMapper = objectMapper;
    }

    /**
     * 重新验证 Release 属于指定实体列表并返回配置摘要。
     *
     * @param target 目标，作为 {@code hasText} 的输入影响后续处理
     * @return 处理后的计算配置摘要文本，供调用方比较或展示
     */
    @Override
    public String configHash(Target target) {
        if (target == null
                || !StringUtils.hasText(target.entityCode())
                || !StringUtils.hasText(target.listKey())
                || !StringUtils.hasText(target.listReleaseId())
                || target.listReleaseVersion() < 1
                || !StringUtils.hasText(target.viewId())
                || target.dependencyClosureVersion()
                != EmbedNativeListDependencyClosure.CURRENT_VERSION
                || target.dependencyClosure() == null
                || target.sessionAbsoluteExpiresAt() == null) {
            throw new IllegalArgumentException("Embed 原生列表目标不完整");
        }
        EntityListConfig owner = listConfigMapper
                .findByEntityCodeAndListKey(
                        target.entityCode(), target.listKey());
        if (owner == null || !StringUtils.hasText(owner.getId())) {
            throw new IllegalArgumentException("Embed 原生列表不存在");
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveServerPinnedRuntimeListRelease(
                        owner.getId(),
                        target.listReleaseId(),
                        target.listReleaseVersion());
        if (resolved.list() == null
                || !Objects.equals(
                target.entityCode(), resolved.list().getEntityCode())
                || !Objects.equals(
                target.listKey(), resolved.list().getListKey())
                || !Objects.equals(
                target.listReleaseId(), resolved.releaseId())
                || !Objects.equals(
                target.listReleaseVersion(), resolved.releaseVersion())) {
            throw new IllegalArgumentException(
                    "Embed 原生列表发布坐标归属不一致");
        }
        validateDependencyClosure(target, owner.getId());
        return releaseService.releases("LIST", owner.getId()).stream().filter(r -> Objects.equals(target.listReleaseId(),r.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("固定列表配置不存在")).getContentHash();
    }

    @Override
    public void requireListOwner(Target target, String ownerId) {
        EntityListConfig list=listConfigMapper.findByEntityCodeAndListKey(target.entityCode(),target.listKey());
        if(list==null||!Objects.equals(ownerId,list.getId()))throw new IllegalArgumentException("关联宿主不属于 Embed 固定列表");
    }

    /** 会话坐标只由认证过滤器提供，闭包从服务端恢复；请求只选择稳定列表标识。 */
    @Override
    public Target authorizeTarget(Target root, String entityCode, String listKey) {
        var session = com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext.currentSession()
                .orElseThrow(() -> new IllegalArgumentException("缺少已认证 Embed 会话"));
        if (!Objects.equals(session.sessionId(),root.sessionId()) || !Objects.equals(session.viewReleaseId(),root.viewReleaseId()))
            throw new IllegalArgumentException("Embed 根目标与认证会话不一致");
        EmbedNativeListDependencyClosure closure = dependencySnapshots.getObject().readForSession(root.sessionId(),root.viewId(),root.viewReleaseId());
        Target verifiedRoot = new Target(root.entityCode(),root.listKey(),root.listReleaseId(),root.listReleaseVersion(),
                root.sessionId(),root.viewId(),root.viewReleaseId(),root.sessionAbsoluteExpiresAt(),closure.version(),null,closure);
        configHash(verifiedRoot);
        ListNode node = closure.nodes().stream().filter(n -> Objects.equals(entityCode,n.list().entityCode())
                && Objects.equals(listKey,n.list().listKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("目标列表不属于 Embed 发布闭包"));
        return new Target(entityCode,listKey,node.list().listReleaseId(),node.list().listReleaseVersion(),root.sessionId(),root.viewId(),
                root.viewReleaseId(),root.sessionAbsoluteExpiresAt(),closure.version(),null,closure);
    }

    /** 先验证宿主发布路径，再输出固定列表坐标，防止客户端以另一个 listKey 替换子列表。 */
    @Override
    public ReferencedList authorizeFormList(String trustedRootFormId, String entityCode, String listKey,
                                           Map<String, Object> runtimeContext) {
        EntityListConfig owner = listConfigMapper.findByEntityCodeAndListKey(entityCode, listKey);
        if (owner == null || !StringUtils.hasText(owner.getId()))
            throw new IllegalArgumentException("宿主引用的列表不存在");
        var resolved = releaseService.resolveReferencedRuntimeListRelease(trustedRootFormId, owner.getId(), runtimeContext);
        if (resolved.list() == null || !Objects.equals(entityCode, resolved.list().getEntityCode())
                || !Objects.equals(listKey, resolved.list().getListKey()))
            throw new IllegalArgumentException("子列表与宿主组件引用不一致");
        return new ReferencedList(entityCode, listKey, resolved.releaseId(), resolved.releaseVersion());
    }

    /** 默认表单和按钮目标从可信列表快照恢复，不接受任意 formId。 */
    @Override
    public String authorizeForm(Target root, String entityCode, String listKey, String formId, Map<String,Object> context) {
        Target target = authorizeTarget(root,entityCode,listKey);
        ListNode node = target.dependencyClosure().nodes().stream().filter(n -> Objects.equals(entityCode,n.list().entityCode())
                && Objects.equals(listKey,n.list().listKey())).findFirst().orElseThrow();
        String buttonId = context == null ? null : Objects.toString(context.get("buttonId"),null);
        if (!StringUtils.hasText(buttonId)) {
            if (node.defaultForm() != null && Objects.equals(formId,node.defaultForm().formId())) return formId;
            throw new IllegalArgumentException("目标表单不是列表默认表单");
        }
        EntityListConfig owner = listConfigMapper.findByEntityCodeAndListKey(entityCode,listKey);
        var list = releaseService.resolveServerPinnedRuntimeListRelease(owner.getId(),target.listReleaseId(),target.listReleaseVersion()).list();
        for (var buttons : java.util.Arrays.asList(list.getToolbarConfig(), list.getRowActionConfig())) {
            if (buttons == null) continue;
            for (var button : buttons) {
                if ((Objects.equals(buttonId,Objects.toString(button.get("id"),null)) || Objects.equals(buttonId,Objects.toString(button.get("key"),null)))
                        && Objects.equals(formId,Objects.toString(button.get("targetFormId"),null))) return formId;
            }
        }
        throw new IllegalArgumentException("按钮未声明目标表单");
    }

    /**
     * 在根列表使用前验证闭包自身、每个 exact Release 以及可选默认表单归属。
     *
     * <p>完整闭包只在服务端方法调用中传递；浏览器只提交稳定列表标识。</p>
     *
     * @param target 目标，作为 {@code coordinateKey} 的输入影响后续处理
     * @param rootListConfigId 根列表配置ID，后续用于校验依赖闭包时定位或关联目标
     */
    private void validateDependencyClosure(
            Target target,
            String rootListConfigId) {
        EmbedNativeListDependencyClosure closure =
                target.dependencyClosure();
        if (closure.version() != target.dependencyClosureVersion()
                || closure.nodes().isEmpty()
                || closure.nodes().size() > 64) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包版本或大小无效");
        }
        Map<String, ListNode> nodes = new HashMap<>();
        int edgeCount = 0;
        for (ListNode node : closure.nodes()) {
            if (node == null || node.list() == null
                    || !node.defaultFormResolved()
                    || node.targets().size() > 256) {
                throw new IllegalArgumentException("Embed LIST 依赖闭包节点无效");
            }
            String key = coordinateKey(node.list());
            if (nodes.putIfAbsent(key, node) != null) {
                throw new IllegalArgumentException("Embed LIST 依赖闭包坐标重复");
            }
            edgeCount += node.targets().size();
            validateListCoordinate(node.list());
            validateDefaultForm(node.list().entityCode(), node.defaultForm());
        }
        if (edgeCount > 256) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包边数超限");
        }
        String rootKey = coordinateKey(new ListCoordinate(
                target.entityCode(), target.listKey(), rootListConfigId,
                target.listReleaseId(), target.listReleaseVersion()));
        if (!nodes.containsKey(rootKey)) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包缺少根节点");
        }
        // 只接受从根可达的节点；否则被篡改追加的任意历史 Release 可能被选择使用。
        Set<String> reachable = new HashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(rootKey);
        while (!pending.isEmpty()) {
            String key = pending.remove();
            if (!reachable.add(key)) {
                continue;
            }
            ListNode node = nodes.get(key);
            if (node == null) {
                throw new IllegalArgumentException("Embed LIST 依赖闭包存在悬空边");
            }
            for (ListCoordinate child : node.targets()) {
                String childKey = coordinateKey(child);
                if (!nodes.containsKey(childKey)) {
                    throw new IllegalArgumentException(
                            "Embed LIST 依赖闭包存在未物化目标");
                }
                pending.add(childKey);
            }
        }
        if (reachable.size() != nodes.size()) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包包含不可达节点");
        }
    }

    /**
     * 校验列表坐标；不满足约束时阻止后续处理。
     *
     * @param coordinate 坐标，作为 {@code listConfigMapper.findByEntityCodeAndListKey} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateListCoordinate(ListCoordinate coordinate) {
        if (coordinate == null
                || !StringUtils.hasText(coordinate.entityCode())
                || !StringUtils.hasText(coordinate.listKey())
                || !StringUtils.hasText(coordinate.listConfigId())
                || !StringUtils.hasText(coordinate.listReleaseId())
                || coordinate.listReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed LIST 依赖坐标不完整");
        }
        EntityListConfig owner = listConfigMapper.findByEntityCodeAndListKey(
                coordinate.entityCode(), coordinate.listKey());
        if (owner == null
                || !Objects.equals(
                coordinate.listConfigId(), owner.getId())) {
            throw new IllegalArgumentException("Embed LIST 依赖列表归属不一致");
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveServerPinnedRuntimeListRelease(
                        owner.getId(), coordinate.listReleaseId(),
                        coordinate.listReleaseVersion());
        if (resolved.list() == null
                || !Objects.equals(
                coordinate.entityCode(), resolved.list().getEntityCode())
                || !Objects.equals(
                coordinate.listKey(), resolved.list().getListKey())) {
            throw new IllegalArgumentException("Embed LIST 依赖 Release 归属不一致");
        }
    }

    /**
     * 校验默认表单；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param form 表单，作为 {@code resolveRuntimeFormRelease} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateDefaultForm(
            String entityCode,
            FormCoordinate form) {
        if (form == null) {
            return;
        }
        if (!StringUtils.hasText(form.formId())
                || !StringUtils.hasText(form.formReleaseId())
                || form.formReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed LIST 默认表单坐标不完整");
        }
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Embed LIST 默认表单实体不存在"));
        ResolvedEntityFormRelease resolved = releaseService
                .resolveRuntimeFormRelease(
                        form.formId(), form.formReleaseId(),
                        form.formReleaseVersion(),
                        UiRuntimeResolutionContext.standalone());
        if (resolved.form() == null
                || !Objects.equals(form.formId(), resolved.form().getId())
                || !Objects.equals(
                definition.getId(), resolved.form().getEntityId())) {
            throw new IllegalArgumentException(
                    "Embed LIST 默认表单 Release 归属不一致");
        }
    }

    /**
     * 生成坐标键文本，供后续匹配或展示。
     *
     * @param coordinate 坐标，供本方法处理坐标键时使用
     * @return 处理后的坐标键文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String coordinateKey(ListCoordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("Embed LIST 依赖坐标为空");
        }
        return String.join("\u001f",
                String.valueOf(coordinate.entityCode()),
                String.valueOf(coordinate.listKey()),
                String.valueOf(coordinate.listConfigId()),
                String.valueOf(coordinate.listReleaseId()),
                String.valueOf(coordinate.listReleaseVersion()));
    }
}
