package com.workflow.entity.data.application;

import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedRelationService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 动态实体关系运行时。
 */
@Slf4j
@Service
public class EntityRelationRuntimeService {

    private static final int MAX_RELATION_DEPTH = 8;
    private static final int MAX_SELF_RELATION_ANCESTORS = 256;

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityPublishHistoryMapper publishHistoryMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final DynamicTableService dynamicTableService;
    private final ObjectMapper objectMapper;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final EntityPublishedRelationService publishedRelationService;
    private final EntityFormUniqueClaimService formUniqueClaimService;

    @Autowired
    public EntityRelationRuntimeService(
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityPublishHistoryMapper publishHistoryMapper,
            EntityFieldMapper fieldMapper,
            EntityRelationMapper relationMapper,
            DynamicTableService dynamicTableService,
            ObjectMapper objectMapper,
            EntityRuntimeRecordMapper recordMapper,
            EntityCodeGeneratorService codeGeneratorService,
            EntityPublishedRelationService publishedRelationService,
            EntityFormUniqueClaimService formUniqueClaimService) {
        this.dynamicMapper = dynamicMapper;
        this.definitionMapper = definitionMapper;
        this.publishHistoryMapper = publishHistoryMapper;
        this.fieldMapper = fieldMapper;
        this.relationMapper = relationMapper;
        this.dynamicTableService = dynamicTableService;
        this.objectMapper = objectMapper;
        this.recordMapper = recordMapper;
        this.codeGeneratorService = codeGeneratorService;
        this.publishedRelationService = publishedRelationService;
        this.formUniqueClaimService = formUniqueClaimService;
    }

    /** 兼容显式装配；生产容器使用包含子表单唯一终检服务的主构造器。 */
    public EntityRelationRuntimeService(
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityPublishHistoryMapper publishHistoryMapper,
            EntityFieldMapper fieldMapper,
            EntityRelationMapper relationMapper,
            DynamicTableService dynamicTableService,
            ObjectMapper objectMapper,
            EntityRuntimeRecordMapper recordMapper,
            EntityCodeGeneratorService codeGeneratorService,
            EntityPublishedRelationService publishedRelationService) {
        this(
                dynamicMapper,
                definitionMapper,
                publishHistoryMapper,
                fieldMapper,
                relationMapper,
                dynamicTableService,
                objectMapper,
                recordMapper,
                codeGeneratorService,
                publishedRelationService,
                null);
    }

    /** 兼容显式装配；生产容器使用包含发布历史 Mapper 的主构造器。 */
    public EntityRelationRuntimeService(
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityRelationMapper relationMapper,
            DynamicTableService dynamicTableService,
            ObjectMapper objectMapper,
            EntityRuntimeRecordMapper recordMapper,
            EntityCodeGeneratorService codeGeneratorService,
            EntityPublishedRelationService publishedRelationService) {
        this(
                dynamicMapper,
                definitionMapper,
                null,
                fieldMapper,
                relationMapper,
                dynamicTableService,
                objectMapper,
                recordMapper,
                codeGeneratorService,
                publishedRelationService,
                null);
    }

    /**
     * 旧单元测试和嵌入式集成的兼容构造器；生产 Spring 容器使用带发布关系解析器的构造器。
     */
    public EntityRelationRuntimeService(
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityRelationMapper relationMapper,
            DynamicTableService dynamicTableService,
            ObjectMapper objectMapper,
            EntityRuntimeRecordMapper recordMapper,
            EntityCodeGeneratorService codeGeneratorService) {
        this(
                dynamicMapper,
                definitionMapper,
                null,
                fieldMapper,
                relationMapper,
                dynamicTableService,
                objectMapper,
                recordMapper,
                codeGeneratorService,
                null,
                null);
    }

    /**
     * 加载实体配置的所有关系定义。
     *
     * @param definition 实体定义
     * @return 关系定义列表（无则返回空列表）
     */
    public List<EntityRelation> loadRelations(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityRelation> relations = publishedRelationService == null
                ? relationMapper.selectByParentEntityId(definition.getId())
                : publishedRelationService.list(definition);
        return relations != null ? relations : List.of();
    }

    /**
     * 在事务内取得实体自关联写入守卫。
     *
     * <p>所有记录写先共享锁实体定义，允许普通记录并发，同时阻止实体发布切换
     * 关系快照。存在自关联时再独占锁当前发布历史行，使同一棵树的写入串行化；
     * 发布事务先独占定义行，故两类锁始终保持一致顺序。</p>
     *
     * @param entityCode 实体编码
     * @throws BusinessConflictException 实体定义在加锁前后不存在时抛出
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSelfRelationGuard(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        EntityDefinition definition = (publishHistoryMapper == null
                ? definitionMapper.findByEntityCode(entityCode.trim())
                : definitionMapper.findByEntityCodeForShare(entityCode.trim()))
                .orElseThrow(() -> new BusinessConflictException(
                        "ENTITY_SELF_RELATION_DEFINITION_NOT_FOUND",
                        "自关联校验失败，实体不存在: " + entityCode));
        if (selfRelations(definition).isEmpty()) {
            return;
        }
        // 仅供不启动 Spring 的遗留单元测试使用；生产主构造器必有发布历史
        // Mapper，并采用共享定义锁 + 发布历史互斥锁的并发路径。
        if (publishHistoryMapper == null) {
            definitionMapper.findByEntityCodeForUpdate(entityCode.trim())
                    .orElseThrow(() -> new BusinessConflictException(
                            "ENTITY_SELF_RELATION_DEFINITION_NOT_FOUND",
                            "自关联校验失败，实体不存在: " + entityCode));
            return;
        }
        if (publishHistoryMapper.findLatestByEntityIdForUpdate(
                definition.getId()) == null) {
            throw new BusinessConflictException(
                    "ENTITY_SELF_RELATION_RELEASE_GUARD_MISSING",
                    "自关联校验失败，当前实体缺少可锁定的发布版本");
        }
    }

    /**
     * 校验同实体父引用写入不会产生自指或祖先循环。
     *
     * <p>调用方必须处于写事务中。本方法先复用实体定义守卫，再锁定当前记录和
     * 每一级祖先并读取数据库权威值；不存在的父记录、既有祖先环和超过安全深度
     * 都按冲突拒绝，不能把不确定状态当作无环。</p>
     *
     * @param definition 当前实体定义
     * @param recordId 当前写入记录 ID；创建场景也必须预先生成稳定 ID
     * @param storageData 即将写入动态表的数据，可使用字段编码或数据库列名
     * @param creating 是否为新增记录
     * @throws BusinessConflictException 父记录不存在或关系图不满足树完整性时抛出
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateSelfRelationWrite(
            EntityDefinition definition,
            String recordId,
            Map<String, Object> storageData,
            boolean creating) {
        if (definition == null
                || !StringUtils.hasText(definition.getEntityCode())) {
            throw new IllegalArgumentException("实体定义不能为空");
        }
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException("自关联写入记录 ID 不能为空");
        }
        // 即便当前还没有自关联，也必须先取得定义共享锁。否则“检查为空”与
        // 首次发布自关联之间存在窗口，旧写事务可能在新关系生效后绕过防环。
        // 同一事务中的嵌套写会再次调用本方法，数据库行锁可重入。
        lockSelfRelationGuard(definition.getEntityCode());
        List<EntityRelation> currentRelations = selfRelations(definition);
        if (currentRelations.isEmpty()) {
            return;
        }

        String normalizedRecordId = recordId.trim();
        String tableName = dynamicTableService.getTableName(
                definition.getEntityCode());
        Map<String, Object> currentRecord = null;
        if (!creating) {
            currentRecord = dynamicMapper.selectByIdForUpdate(
                    tableName, normalizedRecordId);
            if (currentRecord == null) {
                throw new BusinessConflictException(
                        "ENTITY_SELF_RELATION_RECORD_NOT_FOUND",
                        "自关联校验失败，当前记录不存在: "
                                + normalizedRecordId);
            }
        }

        for (EntityRelation relation : currentRelations) {
            EntityField referenceField = requireSelfReferenceField(
                    definition, relation);
            String columnName = StringUtils.hasText(
                    referenceField.getDbColumnName())
                    ? referenceField.getDbColumnName()
                    : recordMapper.toColumnName(
                            referenceField.getFieldCode());
            Object proposedValue = proposedReferenceValue(
                    storageData,
                    currentRecord,
                    referenceField.getFieldCode(),
                    columnName);
            String parentId = normalizeReferenceId(
                    proposedValue,
                    referenceField.getFieldCode());
            validateAncestorChain(
                    tableName,
                    normalizedRecordId,
                    parentId,
                    referenceField.getFieldCode(),
                    columnName);
        }
    }

    /**
     * 从数据中剔除关系字段，返回仅含父表字段的数据副本。
     *
     * @param data       实体数据
     * @param relations 关系定义列表
     * @return 剔除关系字段后的数据副本
     */
    public Map<String, Object> withoutRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return data;
        }
        Map<String, Object> parentData = new HashMap<>(data);
        for (EntityRelation relation : relations) {
            String dataKey = effectiveDataKey(relation);
            if (StringUtils.hasText(dataKey)) {
                parentData.remove(dataKey);
            }
        }
        return parentData;
    }

    /**
     * 从表单提交数据中剔除关系字段，兼容 data 子对象嵌套结构。
     *
     * @param formData 表单提交数据
     * @param relations 关系定义列表
     * @return 剔除关系字段后的表单数据副本
     */
    public Map<String, Object> withoutRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null || formData.isEmpty()) {
            return formData;
        }
        Map<String, Object> parentFormData = new HashMap<>(formData);
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            parentFormData.put("data", withoutRelationData(customData, relations));
        } else {
            parentFormData = withoutRelationData(parentFormData, relations);
        }
        return parentFormData;
    }

    /**
     * 从数据中抽取关系字段取值。
     *
     * @param data       实体数据
     * @param relations 关系定义列表
     * @return 关系字段编码到取值的映射
     */
    public Map<String, Object> extractRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        Map<String, Object> result = new HashMap<>();
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return result;
        }
        for (EntityRelation relation : relations) {
            String dataKey = effectiveDataKey(relation);
            if (StringUtils.hasText(dataKey) && data.containsKey(dataKey)) {
                result.put(dataKey, data.get(dataKey));
            }
        }
        return result;
    }

    /**
     * 从表单提交数据中抽取关系字段取值，兼容 data 子对象嵌套结构。
     *
     * @param formData 表单提交数据
     * @param relations 关系定义列表
     * @return 关系字段编码到取值的映射
     */
    public Map<String, Object> extractRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null) {
            return Map.of();
        }
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            return extractRelationData(customData, relations);
        }
        return extractRelationData(formData, relations);
    }

    /**
     * 递归保存父记录下所有关系子数据：新增/更新传入行，删除未传入的旧行。
     *
     * @param parentId      父记录ID
     * @param relations      关系定义列表
     * @param relationData   关系字段取值（可含多级嵌套子数据）
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void saveRelationData(String parentId, List<EntityRelation> relations, Map<String, Object> relationData) {
        saveRelationData(parentId, relations, relationData, null);
    }

    /**
     * 使用统一变更事务生成的 out-of-band 父计划递归写关系数据。
     * 缺失/漂移的 Marker 或 token 会在任何子业务行锁/写入前失败。
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void saveRelationData(
            String parentId,
            List<EntityRelation> relations,
            Map<String, Object> relationData,
            EntityFormUniqueClaimService.PreparedUniqueClaims prepared) {
        saveRelationData(
                parentId,
                relations,
                relationData,
                prepared,
                1,
                new HashSet<>());
    }

    /**
     * 递归加载实体数据 DTO 的所有关系子数据并回填到 data 中。
     * 一对一关系填充单个对象，一对多关系填充列表。
     *
     * @param dto 实体数据 DTO
     */
    public void loadRelationData(EntityDataDTO dto) {
        if (dto == null || dto.getId() == null || dto.getEntityCode() == null) {
            return;
        }
        EntityDefinition definition = definitionMapper.findByEntityCode(dto.getEntityCode()).orElse(null);
        if (definition == null) {
            return;
        }
        if (dto.getData() == null) {
            dto.setData(new HashMap<>());
        }
        loadRelationData(definition, dto.getId(), dto.getData(), 1, new HashSet<>());
    }

    /**
     * 级联删除父记录下所有标记为级联删除的子记录（递归向下）。
     *
     * @param parentDefinition 父实体定义
     * @param parentId          父记录ID
     * @param physical          true-物理删除 false-逻辑删除
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void cascadeDeleteRelations(EntityDefinition parentDefinition, String parentId, boolean physical) {
        if (parentDefinition == null
                || !StringUtils.hasText(parentDefinition.getEntityCode())
                || !StringUtils.hasText(parentId)) {
            return;
        }
        // 公开删除入口可由不同业务服务调用；调用方必须已开启事务，
        // 且本层仍必须自行取得发布守卫。关系必须在守卫之后读取，
        // 并作为本层递归的冻结输入使用。
        lockSelfRelationGuard(parentDefinition.getEntityCode());
        List<EntityRelation> guardedRelations =
                loadRelations(parentDefinition);
        cascadeDeleteRelations(
                parentDefinition,
                parentId,
                physical,
                guardedRelations,
                1,
                new HashSet<>());
    }

    private void saveRelationData(
            String parentId,
            List<EntityRelation> relations,
            Map<String, Object> relationData,
            EntityFormUniqueClaimService.PreparedUniqueClaims prepared,
            int depth,
            Set<String> path) {
        if (formUniqueClaimService != null) {
            formUniqueClaimService.verifyRelationPrepared(
                    relationData,
                    prepared);
        } else if (prepared != null) {
            throw new IllegalStateException(
                    "可信子表单缺少唯一性协调服务");
        }
        if (formUniqueClaimService != null
                && formUniqueClaimService.requiresRelationWrites(prepared)
                && (relations == null
                || relations.isEmpty()
                || relationData == null
                || relationData.isEmpty()
                || depth > MAX_RELATION_DEPTH)) {
            throw new IllegalStateException(
                    "可信子表单写计划无法由当前关系定义完整执行");
        }
        if (!StringUtils.hasText(parentId) || relations == null || relations.isEmpty()
                || relationData == null || relationData.isEmpty() || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : relations) {
            String dataKey = effectiveDataKey(relation);
            if (!StringUtils.hasText(dataKey) || !relationData.containsKey(dataKey)) {
                continue;
            }
            Object relationValue = relationData.get(dataKey);
            if (relationValue == null) {
                // 前端未提供该关系字段数据时，跳过处理，避免误删已有子表数据
                continue;
            }
            if (relation.getOwnershipType()
                    == EntityRelation.OwnershipType.ASSOCIATION) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_ASSOCIATION_NESTED_WRITE_UNSUPPORTED",
                        "普通关联 " + relation.getRelationName()
                                + " 不能通过父聚合新增、修改或删除子记录");
            }
            List<Map<String, Object>> incomingRows =
                    toRelationRows(relationValue, relation.getRelationType());

            String pathKey = relation.getParentEntityCode() + ":" + dataKey;
            if (!path.add(pathKey)) {
                if (formUniqueClaimService != null
                        && formUniqueClaimService.containsTrustedChildren(
                        relationValue)) {
                    throw new IllegalStateException(
                            "可信嵌套子表单不能因关系循环被静默跳过");
                }
                log.warn("关系存在循环，跳过: {}", pathKey);
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                if (formUniqueClaimService != null
                        && formUniqueClaimService.containsTrustedChildren(
                        relationValue)) {
                    throw new IllegalStateException(
                            "可信子表单关系定义不完整");
                }
                path.remove(pathKey);
                continue;
            }

            ensureEntityTable(childDefinition);
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> existingRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            // 在解释子行 payload 前先冻结子实体关系。嵌套关系字段必须从当前行
            // 的标量存储数据中分离，不能被后续 JSON 规范化吞掉私有 Marker/token。
            lockSelfRelationGuard(childDefinition.getEntityCode());
            List<EntityRelation> childRelations =
                    loadRelations(childDefinition);
            // 同层子表单必须在任何子业务行锁/写入前一次性准备并排序锁定
            // 全部唯一值 gate，避免两事务各持一条新子行后反向等待 gate。
            List<PendingChildWrite> pendingWrites = new ArrayList<>();
            for (Map<String, Object> row : incomingRows) {
                TrustedSubFormUniqueReference.Resolved trusted =
                        TrustedSubFormUniqueReference.removePrepared(row);
                List<FormUniqueMutationContext.Reference> formReferences =
                        trusted.references();
                if (!formReferences.isEmpty()
                        && !StringUtils.hasText(trusted.entityCode())) {
                    throw new IllegalStateException(
                            "可信子表单缺少写前 gate 实体");
                }
                if (StringUtils.hasText(trusted.entityCode())
                        && !childDefinition.getEntityCode().equals(
                                trusted.entityCode())) {
                    throw new IllegalStateException(
                            "子表单写前 gate 实体与关系目标不一致");
                }
                if (!formReferences.isEmpty()
                        && trusted.prepared() == null) {
                    throw new IllegalStateException(
                            "可信子表单缺少父业务写入前 gate 准备");
                }
                Map<String, Object> submitted = new LinkedHashMap<>(row);
                submitted.put(relation.getChildRefFieldCode(), parentId);
                submitted.put("update_by", UserContext.getUserId());
                submitted.put("update_time", LocalDateTime.now());
                submitted.put("deleted", 0);

                String childId = stringValue(submitted.get("id"));
                boolean isNewChild = !StringUtils.hasText(childId);
                if (isNewChild) {
                    childId = generateId();
                    submitted.put("id", childId);
                    submitted.put("create_by", UserContext.getUserId());
                    submitted.put("create_time", LocalDateTime.now());
                    Object existingCode = submitted.get("code");
                    if (existingCode == null
                            || existingCode.toString().trim().isEmpty()) {
                        submitted.put("code", codeGeneratorService
                                .generateCode(
                                        childDefinition.getEntityCode()));
                    }
                }
                // 保留完整 projected record 供唯一性递归复核；仅真正写当前子表
                // 的 childData 可以做 JSON 存储转换，childRelationData 必须保持
                // 原始 Map/List 及 JVM 私有 Marker/token 供下一层递归消费。
                Map<String, Object> childRelationData =
                        extractRelationData(
                                submitted,
                                childRelations);
                Map<String, Object> childData = new LinkedHashMap<>(
                        withoutRelationData(
                                submitted,
                                childRelations));
                if (!formReferences.isEmpty()) {
                    if (formUniqueClaimService == null) {
                        throw new IllegalStateException(
                                "可信子表单缺少唯一性协调服务");
                    }
                    // 父事务已完成 gate 与权威扫描；这里在任何子业务行锁/写入
                    // 前，把父引用、生成 ID/code 等服务端补全字段纳入候选复核。
                    formUniqueClaimService.verifyChildPrepared(
                            childDefinition.getEntityCode(),
                            childId,
                            submitted,
                            formReferences,
                            trusted.prepared());
                }
                normalizeJsonValues(childData);
                pendingWrites.add(new PendingChildWrite(
                        childId,
                        isNewChild,
                        submitted,
                        childData,
                        childRelationData,
                        formReferences,
                        trusted.prepared()));
            }

            // 聚合提交采用“传入集合替换当前集合”语义。先按稳定顺序锁定全部
            // 当前子记录，既避免两个并发提交互相覆盖，也形成不可伪造的归属集合。
            // 客户端携带的已有子 ID 只能来自该集合，不能借父表单把其他父记录
            // 的子项重新挂到当前父记录。
            Set<String> ownedChildIds = lockAndCollectOwnedChildIds(
                    childTableName,
                    relation.getChildRefFieldCode(),
                    parentId,
                    existingRows);
            Set<String> activeIds = new HashSet<>();
            for (PendingChildWrite pending : pendingWrites) {
                String childId = pending.childId();
                boolean isNewChild = pending.newChild();
                Map<String, Object> childRelationData =
                        pending.childRelationData();
                Map<String, Object> childData = pending.childData();
                if (!isNewChild && !ownedChildIds.contains(childId)) {
                    throw new BusinessConflictException(
                            "ENTITY_RELATION_CHILD_OWNERSHIP_CONFLICT",
                            "子记录 " + childId + " 不属于当前父记录，不能随聚合表单修改");
                }
                validateSelfRelationWrite(
                        childDefinition,
                        childId,
                        childData,
                        isNewChild);
                if (isNewChild) {
                    dynamicMapper.insert(childTableName, childData);
                } else {
                    dynamicMapper.update(childTableName, childData);
                }
                activeIds.add(childId);
                saveRelationData(
                        childId,
                        childRelations,
                        childRelationData,
                        pending.prepared(),
                        depth + 1,
                        path);
                reconcileWrittenChild(
                        childDefinition,
                        childTableName,
                        childId,
                        pending.projectedRecord(),
                        pending.formReferences(),
                        pending.prepared());
            }

            deleteMissingRows(
                    childDefinition.getEntityCode(),
                    childTableName,
                    existingRows,
                    activeIds);
            path.remove(pathKey);
        }
    }

    private void loadRelationData(EntityDefinition parentDefinition, String parentId, Map<String, Object> target,
                                  int depth, Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || target == null || depth > MAX_RELATION_DEPTH) {
            return;
        }
        List<EntityRelation> relations = loadRelations(parentDefinition);
        for (EntityRelation relation : relations) {
            String dataKey = effectiveDataKey(relation);
            if (!StringUtils.hasText(dataKey)) {
                continue;
            }

            String pathKey = relation.getParentEntityCode() + ":" + dataKey;
            if (!path.add(pathKey)) {
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                path.remove(pathKey);
                continue;
            }

            if (!dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                target.put(dataKey, emptyRelationValue(relation));
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> rows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            if (relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE
                    && rows != null && rows.size() > 1) {
                throw new BusinessConflictException(
                        "ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION",
                        "一对一关系 " + relation.getRelationName()
                                + " 存在 " + rows.size() + " 条子记录");
            }
            List<Map<String, Object>> childRows = rows == null ? List.of() : rows.stream()
                    .map(row -> {
                        Map<String, Object> childRow = toChildFormRow(row, childDefinition.getEntityCode());
                        String childId = stringValue(childRow.get("id"));
                        loadRelationData(childDefinition, childId, childRow, depth + 1, path);
                        return childRow;
                    })
                    .collect(Collectors.toList());
            if (relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE) {
                target.put(dataKey, childRows.isEmpty() ? null : childRows.get(0));
            } else {
                target.put(dataKey, childRows);
            }
            path.remove(pathKey);
        }
    }

    private void cascadeDeleteRelations(
            EntityDefinition parentDefinition,
            String parentId,
            boolean physical,
            List<EntityRelation> guardedRelations,
            int depth,
            Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : guardedRelations == null
                ? List.<EntityRelation>of() : guardedRelations) {
            String dataKey = effectiveDataKey(relation);
            if (!Boolean.TRUE.equals(relation.getCascadeDelete())
                    || relation.getOwnershipType()
                    == EntityRelation.OwnershipType.ASSOCIATION
                    || !StringUtils.hasText(dataKey)) {
                continue;
            }
            String pathKey = relation.getParentEntityCode() + ":" + dataKey;
            if (!path.add(pathKey)) {
                continue;
            }
            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())
                    || !dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            // 每个子实体都在触碰业务行前取得守卫，并在守卫后冻结其发布关系。
            // 递归只消费该冻结集合，不能在删除途中悄悄切换到另一发布版本。
            lockSelfRelationGuard(childDefinition.getEntityCode());
            List<EntityRelation> childRelations =
                    loadRelations(childDefinition);
            List<Map<String, Object>> childRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            for (Map<String, Object> childRow : childRows) {
                String childId = stringValue(childRow.get("id"));
                if (!StringUtils.hasText(childId)) {
                    continue;
                }
                cascadeDeleteRelations(
                        childDefinition,
                        childId,
                        physical,
                        childRelations,
                        depth + 1,
                        path);
                if (physical) {
                    dynamicMapper.physicalDeleteById(childTableName, childId);
                } else {
                    dynamicMapper.deleteById(childTableName, childId);
                }
                releaseChildClaims(
                        childDefinition.getEntityCode(),
                        childId);
            }
            path.remove(pathKey);
        }
    }

    private EntityDefinition loadChildEntity(EntityRelation relation) {
        EntityDefinition childDefinition = null;
        if (StringUtils.hasText(relation.getChildEntityId())) {
            childDefinition = definitionMapper.selectById(relation.getChildEntityId());
        }
        if (childDefinition == null && StringUtils.hasText(relation.getChildEntityCode())) {
            childDefinition = definitionMapper.findByEntityCode(relation.getChildEntityCode()).orElse(null);
        }
        if (childDefinition != null) {
            childDefinition.setFields(loadEntityFields(childDefinition));
        }
        return childDefinition;
    }

    private List<EntityField> loadEntityFields(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityField> fields = fieldMapper.findByEntityId(definition.getId());
        return fields != null ? fields : List.of();
    }

    private void ensureEntityTable(EntityDefinition definition) {
        if (!dynamicTableService.tableExists(definition.getEntityCode())) {
            dynamicTableService.createEntityTable(definition);
        }
    }

    private List<Map<String, Object>> toRelationRows(Object value, EntityRelation.RelationType relationType) {
        if (value == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) value;
            rows.add(new HashMap<>(row));
            return rows;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) item;
                    rows.add(new HashMap<>(row));
                }
            }
        }
        if (relationType == EntityRelation.RelationType.ONE_TO_ONE && rows.size() > 1) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION",
                    "一对一关系提交数据不能包含多条子记录");
        }
        return rows;
    }

    private List<Map<String, Object>> findRowsByReference(String tableName, String refFieldCode, String parentId) {
        Map<String, Object> condition = new HashMap<>();
        condition.put(refFieldCode, parentId);
        condition.put(refFieldCode + "_op", "EQ");
        List<Map<String, Object>> rows = dynamicMapper.selectByCondition(tableName, condition);
        return rows != null ? rows : List.of();
    }

    /**
     * 锁定当前父记录已经拥有的子记录并返回稳定 ID 集合。
     *
     * <p>关系查询和逐行锁之间仍可能发生直接写入，因此锁后再次核对承载外键；
     * 发现归属改变即终止整个事务，不使用过期快照继续覆盖。</p>
     */
    private Set<String> lockAndCollectOwnedChildIds(
            String tableName,
            String refFieldCode,
            String parentId,
            List<Map<String, Object>> existingRows) {
        List<String> ids = existingRows == null
                ? List.of()
                : existingRows.stream()
                .map(row -> stringValue(row.get("id")))
                .filter(StringUtils::hasText)
                .sorted()
                .toList();
        Set<String> result = new HashSet<>();
        for (String id : ids) {
            Map<String, Object> locked = dynamicMapper.selectByIdForUpdate(
                    tableName, id);
            if (locked == null || !parentId.equals(
                    stringValue(locked.get(refFieldCode)))) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_CHILD_OWNERSHIP_CHANGED",
                        "子记录归属在保存期间发生变化，请刷新后重试");
            }
            result.add(id);
        }
        return Set.copyOf(result);
    }

    /** 返回当前已发布关系中的同实体父引用，所有权类型不影响树完整性约束。 */
    private List<EntityRelation> selfRelations(
            EntityDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        return loadRelations(definition).stream()
                .filter(Objects::nonNull)
                .filter(relation -> !Boolean.FALSE.equals(
                        relation.getEnabled()))
                .filter(relation -> isSameEntity(
                        definition, relation))
                .toList();
    }

    private boolean isSameEntity(
            EntityDefinition definition,
            EntityRelation relation) {
        if (StringUtils.hasText(definition.getId())
                && StringUtils.hasText(relation.getChildEntityId())) {
            return Objects.equals(
                    definition.getId(),
                    relation.getChildEntityId());
        }
        return StringUtils.hasText(definition.getEntityCode())
                && Objects.equals(
                definition.getEntityCode(),
                relation.getChildEntityCode());
    }

    /**
     * 运行时再次确认发布关系的承载字段，旧快照配置不完整时也必须关闭写入。
     */
    private EntityField requireSelfReferenceField(
            EntityDefinition definition,
            EntityRelation relation) {
        if (!StringUtils.hasText(relation.getChildRefFieldCode())) {
            throw new BusinessConflictException(
                    "ENTITY_SELF_RELATION_REF_FIELD_INVALID",
                    "自关联关系缺少父引用字段: "
                            + relation.getRelationName());
        }
        EntityField field = fieldMapper.findByEntityIdAndFieldCode(
                definition.getId(),
                relation.getChildRefFieldCode());
        if (field == null
                || field.getFieldType()
                != EntityField.FieldType.REFERENCE
                || !Objects.equals(
                definition.getId(), field.getRefEntityId())) {
            throw new BusinessConflictException(
                    "ENTITY_SELF_RELATION_REF_FIELD_INVALID",
                    "自关联关系的父引用字段配置无效: "
                            + definition.getEntityCode() + "."
                            + relation.getChildRefFieldCode());
        }
        return field;
    }

    private Object proposedReferenceValue(
            Map<String, Object> storageData,
            Map<String, Object> currentRecord,
            String fieldCode,
            String columnName) {
        if (storageData != null) {
            if (storageData.containsKey(columnName)) {
                return storageData.get(columnName);
            }
            if (storageData.containsKey(fieldCode)) {
                return storageData.get(fieldCode);
            }
        }
        if (currentRecord == null) {
            return null;
        }
        if (currentRecord.containsKey(columnName)) {
            return currentRecord.get(columnName);
        }
        return currentRecord.get(fieldCode);
    }

    private String normalizeReferenceId(
            Object value,
            String fieldCode) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence
                || value instanceof Number
                || value instanceof UUID) {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        }
        throw new BusinessConflictException(
                "ENTITY_SELF_RELATION_PARENT_VALUE_INVALID",
                "自关联父引用必须是单条记录 ID: " + fieldCode);
    }

    /**
     * 从拟设置的直接父级向上逐级锁定并读取，确保校验依据与后续更新处于同一事务。
     */
    private void validateAncestorChain(
            String tableName,
            String recordId,
            String proposedParentId,
            String fieldCode,
            String columnName) {
        if (!StringUtils.hasText(proposedParentId)) {
            return;
        }
        if (recordId.equals(proposedParentId)) {
            throw new BusinessConflictException(
                    "ENTITY_SELF_RELATION_SELF_PARENT",
                    "记录不能把自己设置为父级");
        }

        Set<String> visited = new HashSet<>();
        visited.add(recordId);
        String ancestorId = proposedParentId;
        int ancestorCount = 0;
        while (StringUtils.hasText(ancestorId)) {
            if (++ancestorCount > MAX_SELF_RELATION_ANCESTORS) {
                throw new BusinessConflictException(
                        "ENTITY_SELF_RELATION_DEPTH_EXCEEDED",
                        "自关联祖先层级超过安全上限 "
                                + MAX_SELF_RELATION_ANCESTORS
                                + "，无法确认关系无环");
            }
            if (!visited.add(ancestorId)) {
                throw new BusinessConflictException(
                        "ENTITY_SELF_RELATION_CYCLE",
                        "自关联父级链已存在循环，不能保存当前关系");
            }

            Map<String, Object> ancestor =
                    dynamicMapper.selectByIdForUpdate(
                            tableName, ancestorId);
            if (ancestor == null) {
                throw new BusinessConflictException(
                        "ENTITY_SELF_RELATION_PARENT_NOT_FOUND",
                        "自关联父记录不存在: " + ancestorId);
            }
            Object nextValue = ancestor.containsKey(columnName)
                    ? ancestor.get(columnName)
                    : ancestor.get(fieldCode);
            ancestorId = normalizeReferenceId(nextValue, fieldCode);
        }
    }

    private void deleteMissingRows(
            String childEntityCode,
            String tableName,
            List<Map<String, Object>> existingRows,
            Set<String> activeIds) {
        if (existingRows == null || existingRows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : existingRows) {
            String id = stringValue(row.get("id"));
            if (StringUtils.hasText(id) && !activeIds.contains(id)) {
                dynamicMapper.deleteById(tableName, id);
                releaseChildClaims(childEntityCode, id);
            }
        }
    }

    /**
     * 从动态子表重读已落库的最终行，再执行发布子表单的唯一终检。
     *
     * <p>无可信表单标记时不解析任何规则，只清理该子记录曾经留下的
     * 旧占位。终检异常会继续向外抛出，由父聚合写事务回滚子行及占位。</p>
     */
    private void reconcileWrittenChild(
            EntityDefinition childDefinition,
            String childTableName,
            String childId,
            Map<String, Object> projectedRecord,
            List<FormUniqueMutationContext.Reference> formReferences,
            PreparedUniqueClaims prepared) {
        if (formUniqueClaimService == null) {
            // 仅有显式 new 的遗留单测/嵌入式装配会缺失；Spring 主构造器必须注入。
            return;
        }
        if (formReferences == null || formReferences.isEmpty()) {
            formUniqueClaimService.releaseRecord(
                    childDefinition.getEntityCode(),
                    childId);
            return;
        }
        Map<String, Object> stored = dynamicMapper.selectById(
                childTableName,
                childId);
        if (stored == null) {
            throw new IllegalStateException(
                    "子表单唯一终检无法重读最终记录: "
                            + childId);
        }
        Map<String, Object> finalRecord = new LinkedHashMap<>(
                projectedRecord == null
                        ? Map.of() : projectedRecord);
        finalRecord.putAll(toChildFormRow(stored, childDefinition));
        formUniqueClaimService.reconcileChildRecord(
                childDefinition.getEntityCode(),
                childId,
                finalRecord,
                formReferences,
                prepared);
    }

    private record PendingChildWrite(
            String childId,
            boolean newChild,
            Map<String, Object> projectedRecord,
            Map<String, Object> childData,
            Map<String, Object> childRelationData,
            List<FormUniqueMutationContext.Reference> formReferences,
            PreparedUniqueClaims prepared) {
    }

    /** 关系级联或集合替换删除子行时，同事务释放它的历史占位。 */
    private void releaseChildClaims(
            String childEntityCode,
            String childId) {
        if (formUniqueClaimService != null) {
            formUniqueClaimService.releaseRecord(
                    childEntityCode,
                    childId);
        }
    }

    private Object emptyRelationValue(EntityRelation relation) {
        return relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE ? null : List.of();
    }

    /** 聚合数据键：V2 dataKey 优先，旧关系回退承载字段，最后回退关系编码。 */
    public String effectiveDataKey(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        if (StringUtils.hasText(relation.getDataKey())) {
            return relation.getDataKey();
        }
        if (StringUtils.hasText(relation.getParentFieldCode())) {
            return relation.getParentFieldCode();
        }
        return relation.getRelationCode();
    }

    private Map<String, Object> toChildFormRow(Map<String, Object> row, String entityCode) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        return toChildFormRow(row, definition);
    }

    /** 将存储列视角的动态行还原为表单字段编码视角。 */
    private Map<String, Object> toChildFormRow(
            Map<String, Object> row,
            EntityDefinition definition) {
        List<EntityField> fields = definition == null
                ? List.of()
                : definition.getFields() == null
                        ? loadEntityFields(definition)
                        : definition.getFields();
        String entityCode = definition == null
                ? null : definition.getEntityCode();
        EntityDataDTO childDto = recordMapper.toDto(row, entityCode, fields);
        Map<String, Object> data = new HashMap<>();
        if (childDto.getData() != null) {
            data.putAll(childDto.getData());
        }
        // 把常用系统字段也合并到子表单数据，确保表单字段（如名称、编码等）能正确回显
        putIfPresent(data, "id", childDto.getId());
        putIfPresent(data, "name", childDto.getName());
        putIfPresent(data, "code", childDto.getCode());
        putIfPresent(data, "title", childDto.getTitle());
        putIfPresent(data, "dataNo", childDto.getDataNo());
        putIfPresent(data, "status", childDto.getStatus());
        putIfPresent(data, "deptId", childDto.getDeptId());
        putIfPresent(data, "submitterId", childDto.getSubmitterId());
        putIfPresent(data, "submitterName", childDto.getSubmitterName());
        return data;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private void normalizeJsonValues(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String stringValue && stringValue.isEmpty()) {
                entry.setValue(null);
                continue;
            }
            if (value instanceof Map || value instanceof List) {
                try {
                    entry.setValue(objectMapper.writeValueAsString(value));
                } catch (Exception e) {
                    log.warn("字段 {} 序列化 JSON 失败: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
