package com.workflow.entity.definition.application;

import com.workflow.contracts.entity.port.EntityUserPathPort.*;
import com.workflow.contracts.entity.port.EntityUserPathPort;
import com.workflow.contracts.entity.port.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.*;

/** 设计器和执行器共用的关系解释器；动态实体只使用发布快照，系统关系只使用显式白名单。 */
@Service
@RequiredArgsConstructor
public class EntityUserPathMetadata {
    private final EntityDefinitionMapper definitions;
    private final EntityFieldMapper fields;
    private final EntityPublishedSnapshotService snapshots;
    private final SystemEntityFieldPolicy systemPolicy;
    private final com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper relations;

    /** 系统列的身份语义不能从任意业务字段名称猜测；新增映射须保持历史契约兼容。 */
    private static final Map<String, String> SYSTEM_LINKS = Map.ofEntries(
            Map.entry("sys_user.dept_id", "sys_organization"),
            Map.entry("sys_user.org_id", "sys_organization"),
            Map.entry("sys_organization.parent_id", "sys_organization"),
            Map.entry("sys_organization.create_by", "sys_user"),
            Map.entry("sys_organization.leader_id", "sys_user"),
            Map.entry("sys_user_role.user_id", "sys_user"),
            Map.entry("sys_user_role.role_id", "sys_role"),
            Map.entry("sys_user_group.user_id", "sys_user"),
            Map.entry("sys_user_group.group_id", "sys_group"),
            Map.entry("sys_role_menu.role_id", "sys_role"),
            Map.entry("sys_role_menu.menu_id", "sys_menu"),
            Map.entry("sys_menu.parent_id", "sys_menu"),
            Map.entry("sys_menu.create_by", "sys_user"),
            Map.entry("sys_menu.update_by", "sys_user"),
            Map.entry("sys_dict_item.dict_id", "sys_dict"));

    /** 配置迁移复用同一系统关系白名单，不把同名业务字段误认为系统关系。 */
    public static String systemTarget(String entityCode, String fieldCode) { return SYSTEM_LINKS.get(entityCode + "." + fieldCode); }

    /** 每次编译共用一个版本上下文，同一实体重复出现时始终使用同一发布版本。 */
    public Plan compile(String root, List<Step> path, List<Version> versions) {
        if (path == null || path.size() > EntityUserPathPort.MAX_DEPTH) throw invalid("关系路径最多 8 步");
        Context context = new Context(versions);
        Entity current = context.entity(root);
        List<Hop> hops = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            try {
                Hop hop = hop(context, current, path.get(i));
                Step input = path.get(i);
                if ((StringUtils.hasText(input.sourceEntityCode()) && !current.code().equals(input.sourceEntityCode()))
                        || (StringUtils.hasText(input.targetEntityCode()) && !hop.target().code().equals(input.targetEntityCode())))
                    throw invalid("关系实体坐标与已发布定义不一致");
                hops.add(hop);
                current = hop.target();
            } catch (IllegalArgumentException e) {
                throw invalid("关系路径第 " + (i + 1) + " 步：" + e.getMessage());
            }
        }
        return new Plan(current, List.copyOf(hops), List.copyOf(context.used.values()), List.copyOf(path));
    }

    /** 按所选前缀加载下一层，不扫描业务记录或提前递归展开实体图。 */
    public List<Option> options(String root, List<Step> prefix) {
        Plan plan = compile(root, prefix, List.of());
        if (prefix.size() >= EntityUserPathPort.MAX_DEPTH) return List.of();
        Entity entity = plan.terminal();
        List<Option> result = new ArrayList<>();
        for (EntityField field : entity.fields()) {
            String target;
            try { target = target(entity, field); }
            catch (IllegalArgumentException failure) {
                result.add(new Option(new Step("REFERENCE_FIELD", field.getFieldCode(), null, entity.code()),
                        field.getFieldName(), null, false, false, failure.getMessage()));
                continue;
            }
            if (target == null) continue;
            Step step = new Step(entity.system() ? "SYSTEM_REFERENCE" : "REFERENCE_FIELD", field.getFieldCode(), target, entity.code());
            String label = entity.system() ? switch (field.getFieldCode()) {
                case "dept_id" -> "所属部门"; case "org_id" -> "所属组织";
                case "create_by" -> "创建人"; case "update_by" -> "更新人";
                case "parent_id" -> "上级"; default -> field.getFieldName();
            } : field.getFieldName();
            addOption(result, root, prefix, step, label);
        }
        for (EntityRelation relation : entity.relations()) {
            if (Boolean.FALSE.equals(relation.getEnabled())) continue;
            addOption(result, root, prefix, new Step("RELATION", relation.getRelationCode(), null), relation.getRelationName());
        }
        // 仅枚举已声明的父子关系；编译时再次核对父实体发布快照，不能直接执行当前草稿。
        for (EntityRelation relation : relations.selectAllByChildEntityId(entity.definition().getId())) {
            if (Boolean.FALSE.equals(relation.getEnabled())) continue;
            addOption(result, root, prefix, new Step("PARENT_RELATION", relation.getRelationCode(),
                    relation.getParentEntityCode(), entity.code()), "所属 · " + relation.getRelationName());
        }
        return result;
    }

    private void addOption(List<Option> result, String root, List<Step> prefix, Step step, String label) {
        List<Step> candidate = new ArrayList<>(prefix);
        candidate.add(step);
        try {
            Plan compiled = compile(root, candidate, List.of());
            Hop last = compiled.hops().get(compiled.hops().size() - 1);
            result.add(new Option(new Step(step.type(), step.code(), last.target().code(), last.source().code()), StringUtils.hasText(label) ? label : step.code(),
                    last.target().code(), last.multiple(), "sys_user".equals(last.target().code()), null));
        } catch (IllegalArgumentException e) {
            result.add(new Option(step, StringUtils.hasText(label) ? label : step.code(), null, false, false, e.getMessage()));
        }
    }

    private Hop hop(Context context, Entity source, Step step) {
        if (step == null || step.code() == null || !step.code().matches("[A-Za-z][A-Za-z0-9_]{0,99}"))
            throw invalid("关系编码无效");
        if ("PARENT_RELATION".equals(step.type())) {
            Entity parent = context.entity(step.targetEntityCode());
            EntityRelation relation = parent.relations().stream().filter(r -> !Boolean.FALSE.equals(r.getEnabled())
                    && step.code().equals(r.getRelationCode()) && source.definition().getId().equals(r.getChildEntityId()))
                    .findFirst().orElseThrow(() -> invalid("所属关系不存在或未发布"));
            EntityField field = field(source, relation.getChildRefFieldCode());
            var violation = EntityRelationFieldPolicy.violation(field, parent.definition().getId());
            if (violation != null) throw invalid(violation.message());
            return new Hop(source, parent, field, false, false, false);
        }
        if ("RELATION".equals(step.type())) {
            EntityRelation relation = source.relations().stream().filter(r -> !Boolean.FALSE.equals(r.getEnabled())
                    && step.code().equals(r.getRelationCode())).findFirst().orElseThrow(() -> invalid("关系不存在或未发布"));
            Entity target = context.byId(relation.getChildEntityId());
            EntityField field = field(target, relation.getChildRefFieldCode());
            var violation = EntityRelationFieldPolicy.violation(field, source.definition().getId());
            if (violation != null) throw invalid(violation.message());
            return new Hop(source, target, field, true, relation.getRelationType() != EntityRelation.RelationType.ONE_TO_ONE, false);
        }
        if ("REVERSE_REFERENCE".equals(step.type())) {
            Entity target = context.entity(step.targetEntityCode());
            EntityField field = field(target, step.code());
            if (!source.code().equals(target(target, field))) throw invalid("反向字段未引用当前实体");
            if (!StringUtils.hasText(field.getRefEntityId())) throw invalid("反向引用须使用存储实体主键的现代引用字段");
            return new Hop(source, target, field, true, true, false);
        }
        if (!(source.system() ? "SYSTEM_REFERENCE" : "REFERENCE_FIELD").equals(step.type()))
            throw invalid("关系类型与来源实体不一致");
        EntityField field = field(source, step.code());
        String targetCode = target(source, field);
        if (targetCode == null) throw invalid("字段不是可遍历实体关系");
        Entity target = context.entity(targetCode);
        boolean legacyUser = !source.system() && !StringUtils.hasText(field.getRefEntityId()) && "sys_user".equals(targetCode);
        return new Hop(source, target, field, false, field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE, legacyUser);
    }

    private EntityField field(Entity entity, String code) {
        return entity.fields().stream().filter(f -> Objects.equals(code, f.getFieldCode()))
                .filter(f -> !entity.system() || systemPolicy.isRuntimeReadable(entity.definition(), f)).findFirst()
                .orElseThrow(() -> invalid("字段不存在或未发布: " + entity.code() + "." + code));
    }

    /** 现代 refEntityId 是权威目标；旧字段类型只在没有现代引用时适用。 */
    private String target(Entity source, EntityField field) {
        if (source.system()) return SYSTEM_LINKS.get(source.code() + "." + field.getFieldCode());
        if (field.getFieldType() == null) return null;
        if (!Set.of(EntityField.FieldType.USER, EntityField.FieldType.DEPT, EntityField.FieldType.REFERENCE,
                EntityField.FieldType.MULTI_REFERENCE).contains(field.getFieldType())) return null;
        if (StringUtils.hasText(field.getRefEntityId())) {
            EntityDefinition target = definitions.selectById(field.getRefEntityId());
            if (target == null) throw invalid("关系目标实体已删除: " + field.getFieldCode());
            return target.getEntityCode();
        }
        String type = field.getRefEntityType() == null ? field.getFieldType().name() : field.getRefEntityType().name();
        return switch (type) {
            case "USER" -> "sys_user"; case "DEPT" -> "sys_organization";
            case "ROLE" -> "sys_role"; case "GROUP" -> "sys_group";
            default -> null;
        };
    }

    private final class Context {
        private final Map<String, Version> pinned = new LinkedHashMap<>();
        private final Map<String, Version> used = new LinkedHashMap<>();
        private final Map<String, Entity> loaded = new LinkedHashMap<>();
        private final boolean frozen;
        Context(List<Version> versions) {
            frozen = versions != null && !versions.isEmpty();
            for (Version v : versions == null ? List.<Version>of() : versions) {
                if (v == null || pinned.putIfAbsent(v.entityCode(), v) != null) throw invalid("实体版本重复或为空");
            }
        }
        Entity byId(String id) {
            EntityDefinition definition = definitions.selectById(id);
            if (definition == null) throw invalid("关系目标实体不存在");
            return entity(definition.getEntityCode());
        }
        Entity entity(String code) {
            if (!StringUtils.hasText(code)) throw invalid("实体编码不能为空");
            if (loaded.containsKey(code)) return loaded.get(code);
            EntityDefinition definition = definitions.findByEntityCode(code).orElseThrow(() -> invalid("实体不存在: " + code));
            Version version = pinned.get(code);
            if (frozen && version == null) throw invalid("缺少实体的发布版本: " + code);
            Entity entity;
            if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                if (!systemPolicy.isSupportedEntity(code)) throw invalid("不支持的系统实体: " + code);
                if (version != null && (!Objects.equals(version.systemVersion(), 1) || version.historyId() != null || version.schemaHash() != null))
                    throw invalid("系统关系契约版本不匹配");
                used.put(code, new Version(code, null, null, 1));
                entity = new Entity(definition, fields.findByEntityId(definition.getId()), List.of(), true);
            } else {
                var pinnedSnapshot = version == null ? snapshots.getLatestPinnedByEntityId(definition.getId())
                        : snapshots.getPinnedByHistoryId(version.historyId());
                EntityPublishedSnapshot snapshot = pinnedSnapshot.snapshot();
                if (!code.equals(snapshot.getEntityCode()) || !definition.getId().equals(snapshot.getEntityId())
                        || (version != null && (!Objects.equals(version.schemaHash(), pinnedSnapshot.schemaHash()) || version.systemVersion() != null)))
                    throw invalid("实体发布版本或指纹不匹配: " + code);
                used.put(code, new Version(code, snapshot.getHistoryId(), pinnedSnapshot.schemaHash(), null));
                entity = new Entity(definition, snapshot.getFields(), snapshot.getRelations(), false);
            }
            loaded.put(code, entity);
            return entity;
        }
    }

    public static EntityUserReferenceException invalid(String message) {
        return new EntityUserReferenceException("ENTITY_USER_PATH_INVALID", message);
    }
    public record Entity(EntityDefinition definition, List<EntityField> fields, List<EntityRelation> relations, boolean system) {
        public Entity { fields = fields == null ? List.of() : List.copyOf(fields); relations = relations == null ? List.of() : List.copyOf(relations); }
        public String code() { return definition.getEntityCode(); }
    }
    /** reverse 时 field 位于目标实体；legacyUser 仅允许旧用户字段的 username 兼容。 */
    public record Hop(Entity source, Entity target, EntityField field, boolean reverse, boolean multiple, boolean legacyUser) { }
    public record Plan(Entity terminal, List<Hop> hops, List<Version> versions, List<Step> steps) {
        public boolean multiple() { return hops.stream().anyMatch(Hop::multiple); }
        public PathDescription describe() { return new PathDescription(terminal.code(), multiple(), "sys_user".equals(terminal.code()), versions, java.util.stream.IntStream.range(0, hops.size()).mapToObj(i -> new Step(steps.get(i).type(), steps.get(i).code(), hops.get(i).target().code(), hops.get(i).source().code())).toList()); }
    }
}
