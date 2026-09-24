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
import com.workflow.entity.definition.application.code.EntityCodeGenerationInput;
import com.workflow.entity.definition.application.EntityPublishedRelationService;
import com.workflow.entity.definition.application.EntityRelationFieldPolicy;
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

    @org.springframework.beans.factory.annotation.Autowired
    private EntityTaskSummaryRefresh taskSummaryRefresh;

    /** 子记录可能有独立流程；随父聚合编辑/删除时也必须刷新其任务摘要。 */
    private void refreshTaskSummary(String entityCode, String recordId) {
        if (taskSummaryRefresh != null) taskSummaryRefresh.changed(entityCode, recordId);
    }


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

    /**
     * 初始化实体关系运行时服务，保存构造参数供后续方法使用。
     *
     * @param dynamicMapper 动态映射器依赖，保存到当前对象供后续业务方法调用
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param publishHistoryMapper 发布历史映射器依赖，保存到当前对象供后续业务方法调用
     * @param fieldMapper 字段映射器依赖，保存到当前对象供后续业务方法调用
     * @param relationMapper 关系映射器依赖，保存到当前对象供后续业务方法调用
     * @param dynamicTableService 动态表服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param recordMapper 记录映射器依赖，保存到当前对象供后续业务方法调用
     * @param codeGeneratorService 编码生成器服务依赖，保存到当前对象供后续业务方法调用
     * @param publishedRelationService 已发布关系服务依赖，保存到当前对象供后续业务方法调用
     * @param formUniqueClaimService 表单唯一认领服务依赖，保存到当前对象供后续业务方法调用
     */
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

    /**
     * 兼容显式装配；生产容器使用包含子表单唯一终检服务的主构造器。
     *
     * @param dynamicMapper 动态映射器，保存在对象中供后续校验、查询或展示
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param publishHistoryMapper 发布历史映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param relationMapper 关系映射器，保存在对象中供后续校验、查询或展示
     * @param dynamicTableService 动态表服务，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param recordMapper 记录映射器，保存在对象中供后续校验、查询或展示
     * @param codeGeneratorService 编码生成器服务，保存在对象中供后续校验、查询或展示
     * @param publishedRelationService 已发布关系服务，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 兼容显式装配；生产容器使用包含发布历史 Mapper 的主构造器。
     *
     * @param dynamicMapper 动态映射器，保存在对象中供后续校验、查询或展示
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param relationMapper 关系映射器，保存在对象中供后续校验、查询或展示
     * @param dynamicTableService 动态表服务，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param recordMapper 记录映射器，保存在对象中供后续校验、查询或展示
     * @param codeGeneratorService 编码生成器服务，保存在对象中供后续校验、查询或展示
     * @param publishedRelationService 已发布关系服务，保存在对象中供后续校验、查询或展示
     */
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
     *
     * @param dynamicMapper 动态映射器，保存在对象中供后续校验、查询或展示
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param relationMapper 关系映射器，保存在对象中供后续校验、查询或展示
     * @param dynamicTableService 动态表服务，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param recordMapper 记录映射器，保存在对象中供后续校验、查询或展示
     * @param codeGeneratorService 编码生成器服务，保存在对象中供后续校验、查询或展示
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
     * <p>所有记录写先取得实体定义读守卫，阻止实体发布切换关系快照。
     * 支持行共享锁的产品允许普通记录并发；其他产品以独占行锁保护。
     * 存在自关联时再独占锁当前发布历史行，使同一棵树的写入串行化；
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
        if (data == null || data.isEmpty()) {
            return data;
        }
        Map<String, Object> parentData = new HashMap<>(data);
        // 即使当前发布版本没有启用关系，也要返回可修改副本；后续还需剔除
        // 旧表单提交的停用关系键，不能修改调用方原始 payload。
        if (relations == null || relations.isEmpty()) {
            return parentData;
        }
        for (EntityRelation relation : relations) {
            if (relation == null) {
                continue;
            }
            String dataKey = effectiveDataKey(relation);
            String relationCode = relation.getRelationCode();
            // 旧表单可能仍用关系编码提交，而新版关系已改用独立 dataKey。
            // 两种键都不是父表物理列，尤其停用后不能漏入父表 INSERT。
            boolean hasDataKey = StringUtils.hasText(dataKey)
                    && data.containsKey(dataKey);
            boolean hasCodeAlias = StringUtils.hasText(relationCode)
                    && !relationCode.equals(dataKey)
                    && data.containsKey(relationCode);
            if (Boolean.FALSE.equals(relation.getEnabled())
                    && ((hasDataKey && hasRelationContent(data.get(dataKey)))
                    || (hasCodeAlias
                    && hasRelationContent(data.get(relationCode))))) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_DISABLED_FORM_STALE",
                        "关系“" + relation.getRelationName()
                                + "”已停用，当前表单仍包含该关系；请更新并重新发布表单后重试");
            }
            if (StringUtils.hasText(dataKey)) {
                parentData.remove(dataKey);
            }
            if (StringUtils.hasText(relationCode)) {
                parentData.remove(relationCode);
            }
        }
        return parentData;
    }

    /**
     * 拦截已从发布快照中消失、但旧表单仍提交的关系键。
     *
     * <p>发布快照只保存启用关系，停用后它不再出现在 {@code loadRelations}
     * 中；此处用当前关系草稿识别其旧键，但仅处理发布快照没有的关系，避免
     * “草稿刚停用、尚未发布”时提前改变已发布关系的运行行为。空值从父表
     * payload 中剔除，非空值明确要求重发旧表单，不能作为不存在的物理列入库。</p>
     *
     * @param definition 当前父实体
     * @param requestData 原始表单提交，可含 data 子对象
     * @param parentData 已复制并剔除启用关系的父表数据
     * @param publishedRelations 当前已发布的启用关系
     * @throws BusinessConflictException 旧表单仍提交已停用关系的实际子数据时抛出
     */
    public void stripUnpublishedRelationKeys(
            EntityDefinition definition,
            Map<String, Object> requestData,
            Map<String, Object> parentData,
            List<EntityRelation> publishedRelations) {
        if (definition == null || !StringUtils.hasText(definition.getId())
                || requestData == null || parentData == null) {
            return;
        }
        List<EntityRelation> draftRelations =
                relationMapper.selectAllByParentEntityId(definition.getId());
        if (draftRelations == null || draftRelations.isEmpty()) {
            return;
        }
        Map<String, Object> source = nestedData(requestData);
        Map<String, Object> target = nestedData(parentData);
        for (EntityRelation draft : draftRelations) {
            if (draft == null || (publishedRelations != null
                    && publishedRelations.stream().anyMatch(relation ->
                    relation != null && java.util.Objects.equals(
                            relation.getRelationCode(), draft.getRelationCode())))) {
                continue;
            }
            String dataKey = effectiveDataKey(draft);
            String relationCode = draft.getRelationCode();
            boolean hasDataKey = StringUtils.hasText(dataKey)
                    && source.containsKey(dataKey);
            boolean hasCodeAlias = StringUtils.hasText(relationCode)
                    && !relationCode.equals(dataKey)
                    && source.containsKey(relationCode);
            if ((hasDataKey && hasRelationContent(source.get(dataKey)))
                    || (hasCodeAlias
                    && hasRelationContent(source.get(relationCode)))) {
                throw new BusinessConflictException(
                        "ENTITY_RELATION_DISABLED_FORM_STALE",
                        "关系“" + draft.getRelationName()
                                + "”未在当前实体版本启用；请更新并重新发布表单后重试");
            }
            if (hasDataKey) {
                target.remove(dataKey);
            }
            if (hasCodeAlias) {
                target.remove(relationCode);
            }
        }
    }

    /** 读取表单协议中真正承载业务字段的 Map，供关系键清理使用。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedData(Map<String, Object> formData) {
        Object data = formData.get("data");
        return data instanceof Map<?, ?> ? (Map<String, Object>) data : formData;
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
            if (relation == null || Boolean.FALSE.equals(relation.getEnabled())) {
                continue;
            }
            String dataKey = effectiveDataKey(relation);
            if (StringUtils.hasText(dataKey) && data.containsKey(dataKey)) {
                result.put(dataKey, data.get(dataKey));
            } else if (StringUtils.hasText(dataKey)
                    && StringUtils.hasText(relation.getRelationCode())
                    && data.containsKey(relation.getRelationCode())) {
                // 老发布表单仍可能把关系编码作为控件键；统一归一到新 dataKey。
                result.put(dataKey, data.get(relation.getRelationCode()));
            }
        }
        return result;
    }

    /**
     * 判断停用关系的旧表单是否仍提交了实际子数据。
     * 空值按“未填写”处理以便旧表单的主记录仍可保存，非空值必须提示表单失配。
     */
    private boolean hasRelationContent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof CharSequence text) {
            return StringUtils.hasText(text);
        }
        return true;
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
     *
     * @param parentId 父级ID，后续用于保存关系数据时定位或关联目标
     * @param relations 关系集合，供本方法保存关系数据时使用
     * @param relationData 关系数据，供本方法保存关系数据时使用
     * @param prepared 已准备，供本方法保存关系数据时使用
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
                new HashSet<>(), "root");
    }

    /**
     * 递归加载实体数据 DTO 的所有关系子数据并回填到 data 中。
     * 一对一关系填充单个对象，一对多关系填充列表。
     *
     * @param dto 实体数据 DTO
     * @return 符合条件的实体关系结果，供调用方继续处理
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

    /**
     * 保存关系数据；后续读取或执行将使用更新后的状态。
     *
     * @param parentId 父级ID，后续用于保存关系数据时定位或关联目标
     * @param relations 关系集合，供本方法保存关系数据时使用
     * @param relationData 关系数据，作为 {@code formUniqueClaimService.verifyRelationPrepared} 的输入影响后续处理
     * @param prepared 已准备，作为 {@code formUniqueClaimService.verifyRelationPrepared} 的输入影响后续处理
     * @param depth 深度，供本方法保存关系数据时使用
     * @param path 路径，供本方法保存关系数据时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void saveRelationData(
            String parentId,
            List<EntityRelation> relations,
            Map<String, Object> relationData,
            EntityFormUniqueClaimService.PreparedUniqueClaims prepared,
            int depth,
            Set<String> path,
            String submissionPath) {
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
                || depth > MAX_RELATION_DEPTH) {
            return;
        }
        validateRequiredRelations(parentId, relations, relationData);
        if (relationData == null || relationData.isEmpty()) {
            return;
        }
        for (EntityRelation relation : relations) {
            if (relation == null || Boolean.FALSE.equals(relation.getEnabled())) {
                continue;
            }
            String dataKey = effectiveDataKey(relation);
            if (!StringUtils.hasText(dataKey) || !relationData.containsKey(dataKey)) {
                continue;
            }
            Object relationValue = relationData.get(dataKey);
            if (relationValue == null
                    && relation.getRelationType()
                    != EntityRelation.RelationType.ONE_TO_ONE) {
                // 一对多的 null 沿用旧协议“保持现状”；一对一的显式 null
                // 表示用户移除当前子记录，缺键才表示未提交该关系。
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
            int rowIndex = 0;
            for (Map<String, Object> row : incomingRows) {
                String childPath = submissionPath + "/" + dataKey + "/" + rowIndex++;
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
                if (isNewChild) {
                    // 先移除嵌套关系私有令牌，再把业务字段传给生成器；当前子行尚未 INSERT。
                    String generatedCode = codeGeneratorService.generateCode(new EntityCodeGenerationInput(
                            childDefinition.getEntityCode(), childId, childData,
                            relation.getParentEntityCode(), parentId, childPath,
                            stringValue(submitted.get("code"))));
                    submitted.put("code", generatedCode);
                    childData.put("code", generatedCode);
                } else {
                    // 更新子行只沿用既有编号，不能借父表单绕过只生成一次的约束。
                    childData.remove("code");
                    submitted.remove("code");
                    String existingChildId = childId;
                    existingRows.stream().filter(existing -> existingChildId.equals(stringValue(existing.get("id"))))
                            .findFirst().ifPresent(existing -> submitted.put("code", existing.get("code")));
                }
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
                        trusted.prepared(), childPath));
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
                refreshTaskSummary(childDefinition.getEntityCode(), childId);
                activeIds.add(childId);
                saveRelationData(
                        childId,
                        childRelations,
                        childRelationData,
                        pending.prepared(),
                        depth + 1,
                        path, pending.submissionPath());
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

    /**
     * 校验必填关系在本次聚合提交后的子记录集合非空。
     *
     * <p>提交了空集合表示替换并删除旧子行；未提交关系键则保留旧子行，
     * 因此需要查询已有归属，不能把两种情况都当作“有旧数据即可通过”。
     * 该校验在父子写入事务内执行，失败会回滚已经写入的父记录。</p>
     *
     * @param parentId 当前父记录 ID
     * @param relations 当前已发布的关系定义
     * @param relationData 本次提交的关系数据，缺键表示保持已有子记录
     * @throws BusinessConflictException 必填关系在提交后没有子记录时抛出
     */
    private void validateRequiredRelations(
            String parentId,
            List<EntityRelation> relations,
            Map<String, Object> relationData) {
        for (EntityRelation relation : relations) {
            if (relation == null
                    || !Boolean.TRUE.equals(relation.getRequired())
                    || Boolean.FALSE.equals(relation.getEnabled())) {
                continue;
            }
            String dataKey = effectiveDataKey(relation);
            boolean submitted = StringUtils.hasText(dataKey)
                    && relationData != null
                    && relationData.containsKey(dataKey)
                    && (relationData.get(dataKey) != null
                    || relation.getRelationType()
                    == EntityRelation.RelationType.ONE_TO_ONE);
            if (submitted) {
                if (!toRelationRows(
                        relationData.get(dataKey),
                        relation.getRelationType()).isEmpty()) {
                    continue;
                }
            } else {
                EntityDefinition childDefinition = loadChildEntity(relation);
                if (childDefinition != null
                        && StringUtils.hasText(childDefinition.getEntityCode())
                        && StringUtils.hasText(relation.getChildRefFieldCode())
                        && dynamicTableService.tableExists(childDefinition.getEntityCode())
                        && !findRowsByReference(
                                dynamicTableService.getTableName(childDefinition.getEntityCode()),
                                relation.getChildRefFieldCode(),
                                parentId).isEmpty()) {
                    continue;
                }
            }
            throw new BusinessConflictException(
                    "ENTITY_RELATION_REQUIRED_MISSING",
                    "关系 " + relation.getRelationName() + " 至少需要一条子记录");
        }
    }

    /**
     * 加载关系数据；查询结果供调用方展示或继续处理。
     *
     * @param parentDefinition 父级定义，作为 {@code loadRelations} 的输入影响后续处理
     * @param parentId 父级ID，后续用于加载关系数据时定位或关联目标
     * @param target 目标，供本方法加载关系数据时使用
     * @param depth 深度，供本方法加载关系数据时使用
     * @param path 路径，供本方法加载关系数据时使用
     * @return 符合条件的实体关系结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 处理{@code cascade}删除关系集合，并将结果传给后续步骤。
     *
     * @param parentDefinition 父级定义，供本方法处理{@code cascade}删除关系集合时使用
     * @param parentId 父级ID，后续用于处理{@code cascade}删除关系集合时定位或关联目标
     * @param physical 物理，供本方法处理{@code cascade}删除关系集合时使用
     * @param guardedRelations {@code guarded}关系集合，供本方法处理{@code cascade}删除关系集合时使用
     * @param depth 深度，供本方法处理{@code cascade}删除关系集合时使用
     * @param path 路径，供本方法处理{@code cascade}删除关系集合时使用
     */
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
                refreshTaskSummary(childDefinition.getEntityCode(), childId);
                releaseChildClaims(
                        childDefinition.getEntityCode(),
                        childId);
            }
            path.remove(pathKey);
        }
    }

    /**
     * 加载子级实体；查询结果供调用方展示或继续处理。
     *
     * @param relation 关系，作为 {@code definitionMapper.selectById} 的输入影响后续处理
     * @return 符合条件的实体定义结果，供调用方继续处理
     */
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

    /**
     * 加载实体字段；查询结果供调用方展示或继续处理。
     *
     * @param definition 定义，作为 {@code fieldMapper.findByEntityId} 的输入影响后续处理
     * @return 实体字段集合，供调用方遍历或展示
     */
    private List<EntityField> loadEntityFields(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityField> fields = fieldMapper.findByEntityId(definition.getId());
        return fields != null ? fields : List.of();
    }

    /**
     * 确保实体表；不满足约束时阻止后续处理。
     *
     * @param definition 定义，作为 {@code dynamicTableService.createEntityTable} 的输入影响后续处理
     */
    private void ensureEntityTable(EntityDefinition definition) {
        if (!dynamicTableService.tableExists(definition.getEntityCode())) {
            dynamicTableService.createEntityTable(definition);
        }
    }

    /**
     * 转换为关系行；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为关系行的原始输入，结果供调用方继续使用
     * @param relationType 关系类型标识，决定后续关系行采用的处理分支
     * @return 实体关系集合，供调用方遍历或展示
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 按引用查询实体关系；结果供后续展示或处理。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param refFieldCode 引用字段编码，后续用于查询行引用时定位或关联目标
     * @param parentId 父级ID，后续用于查询行引用时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
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
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param refFieldCode 引用字段编码，后续用于锁定与{@code collect}{@code owned}子级ID 集合时定位或关联目标
     * @param parentId 父级ID，后续用于锁定与{@code collect}{@code owned}子级ID 集合时定位或关联目标
     * @param existingRows 已有行，供本方法锁定与{@code collect}{@code owned}子级ID 集合时使用
     * @return 实体关系集合，供调用方遍历或展示
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

    /**
     * 返回当前已发布关系中的同实体父引用，所有权类型不影响树完整性约束。
     *
     * @param definition 定义，作为 {@code loadRelations} 的输入影响后续处理
     * @return 实体关系集合，供调用方遍历或展示
     */
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

    /**
     * 判断是否相同实体；判断结果决定调用方的后续分支。
     *
     * @param definition 定义，供本方法判断是否相同实体时使用
     * @param relation 关系，供本方法判断是否相同实体时使用
     * @return 相同实体条件成立时为 true，否则为 false
     */
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
     *
     * @param definition 定义，作为 {@code fieldMapper.findByEntityIdAndFieldCode} 的输入影响后续处理
     * @param relation 关系，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 校验并获取后的{@code self}引用字段结果，供调用方继续处理
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
        if (EntityRelationFieldPolicy.violation(field, definition.getId()) != null) {
            throw new BusinessConflictException(
                    "ENTITY_SELF_RELATION_REF_FIELD_INVALID",
                    "自关联关系的父引用字段配置无效: "
                            + definition.getEntityCode() + "."
                            + relation.getChildRefFieldCode());
        }
        return field;
    }

    /**
     * 处理{@code proposed}引用值，并将结果传给后续步骤。
     *
     * @param storageData 存储数据，供本方法处理{@code proposed}引用值时使用
     * @param currentRecord 当前记录，供本方法处理{@code proposed}引用值时使用
     * @param fieldCode 字段编码，后续用于处理{@code proposed}引用值时定位或关联目标
     * @param columnName 列名称，后续用于处理{@code proposed}引用值时匹配或展示
     * @return 处理后的{@code proposed}引用值结果，供调用方继续处理
     */
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

    /**
     * 规范化引用ID；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化引用ID的原始输入，结果供调用方继续使用
     * @param fieldCode 字段编码，后续用于规范化引用ID时定位或关联目标
     * @return 规范化后的引用ID文本，供调用方比较或展示
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param proposedParentId {@code proposed}父级ID，后续用于校验{@code ancestor}链时定位或关联目标
     * @param fieldCode 字段编码，后续用于校验{@code ancestor}链时定位或关联目标
     * @param columnName 列名称，后续用于校验{@code ancestor}链时匹配或展示
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

    /**
     * 删除缺失行；后续读取或执行将使用更新后的状态。
     *
     * @param childEntityCode 子级实体编码，后续用于删除缺失行时定位或关联目标
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param existingRows 已有行，供本方法删除缺失行时使用
     * @param activeIds 活动ID 集合，供本方法删除缺失行时使用
     */
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
                refreshTaskSummary(childEntityCode, id);
                releaseChildClaims(childEntityCode, id);
            }
        }
    }

    /**
     * 从动态子表重读已落库的最终行，再执行发布子表单的唯一终检。
     *
     * <p>无可信表单标记时不解析任何规则，只清理该子记录曾经留下的
     * 旧占位。终检异常会继续向外抛出，由父聚合写事务回滚子行及占位。</p>
     *
     * @param childDefinition 子级定义，作为 {@code formUniqueClaimService.releaseRecord} 的输入影响后续处理
     * @param childTableName 子级表名称，后续用于对账{@code written}子级时匹配或展示
     * @param childId 子级ID，后续用于对账{@code written}子级时定位或关联目标
     * @param projectedRecord {@code projected}记录，供本方法对账{@code written}子级时使用
     * @param formReferences 表单引用，供本方法对账{@code written}子级时使用
     * @param prepared 已准备，供本方法对账{@code written}子级时使用
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

    /**
     * 封装待处理子级写入的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param childId 子级ID，后续用于处理待处理子级写入时定位或关联目标
     * @param newChild 新子级，保存在对象中供后续校验、查询或展示
     * @param projectedRecord {@code projected}记录，保存在对象中供后续校验、查询或展示
     * @param childData 子级数据，保存在对象中供后续校验、查询或展示
     * @param childRelationData 子级关系数据，保存在对象中供后续校验、查询或展示
     * @param formReferences 表单引用，保存在对象中供后续校验、查询或展示
     * @param prepared 已准备，保存在对象中供后续校验、查询或展示
     */
    private record PendingChildWrite(
            String childId,
            boolean newChild,
            Map<String, Object> projectedRecord,
            Map<String, Object> childData,
            Map<String, Object> childRelationData,
            List<FormUniqueMutationContext.Reference> formReferences,
            PreparedUniqueClaims prepared, String submissionPath) {
    }

    /**
     * 关系级联或集合替换删除子行时，同事务释放它的历史占位。
     *
     * @param childEntityCode 子级实体编码，后续用于处理发布版本子级声明集合时定位或关联目标
     * @param childId 子级ID，后续用于处理发布版本子级声明集合时定位或关联目标
     */
    private void releaseChildClaims(
            String childEntityCode,
            String childId) {
        if (formUniqueClaimService != null) {
            formUniqueClaimService.releaseRecord(
                    childEntityCode,
                    childId);
        }
    }

    /**
     * 处理空关系值，并将结果传给后续步骤。
     *
     * @param relation 关系，供本方法处理空关系值时使用
     * @return 处理后的空关系值结果，供调用方继续处理
     */
    private Object emptyRelationValue(EntityRelation relation) {
        return relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE ? null : List.of();
    }

    /**
     * 聚合数据键：V2 dataKey 优先，旧关系回退承载字段，最后回退关系编码。
     *
     * @param relation 关系，供本方法处理有效数据键时使用
     * @return 处理后的有效数据键文本，供调用方比较或展示
     */
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

    /**
     * 转换为子级表单行；输出作为后续校验或处理的输入。
     *
     * @param row 行，供本方法转换为子级表单行时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 子级表单行键值结果，供调用方继续处理
     */
    private Map<String, Object> toChildFormRow(Map<String, Object> row, String entityCode) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        return toChildFormRow(row, definition);
    }

    /**
     * 将存储列视角的动态行还原为表单字段编码视角。
     *
     * @param row 行，作为 {@code recordMapper.toDto} 的输入影响后续处理
     * @param definition 定义，供本方法转换为子级表单行时使用
     * @return 子级表单行键值结果，供调用方继续处理
     */
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
        putIfPresent(data, "status", childDto.getStatus());
        putIfPresent(data, "deptId", childDto.getDeptId());
        putIfPresent(data, "submitterId", childDto.getSubmitterId());
        putIfPresent(data, "submitterName", childDto.getSubmitterName());
        return data;
    }

    /**
     * 写入条件存在；后续读取或执行将使用更新后的状态。
     *
     * @param map 映射，供本方法写入条件存在时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待写入条件存在的原始输入，结果供调用方继续使用
     */
    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * 规范化JSON值集合；输出作为后续校验或处理的输入。
     *
     * @param data 数据，后续用于规范化JSON值集合并传递处理结果
     */
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

    /**
     * 生成字符串值文本，供后续匹配或展示。
     *
     * @param value 待处理字符串值的原始输入，结果供调用方继续使用
     * @return 处理后的字符串值文本，供调用方比较或展示
     */
    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 生成ID；结果供调用方的后续步骤使用。
     *
     * @return 生成后的ID文本，供调用方比较或展示
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
