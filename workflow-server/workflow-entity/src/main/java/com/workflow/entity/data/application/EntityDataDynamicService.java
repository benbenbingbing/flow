package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 动态实体聚合查询服务。
 *
 * <p>业务数据写入统一由 EntityMutationPort 进入变更管道，本服务不提供写方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataDynamicService {

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityRelationRuntimeService relationRuntimeService;
    private final EntityMultiValueRuntimeService multiValueRuntimeService;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService sysUserService;
    private final EntityPublishedSnapshotService snapshotService;

    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode) {
        return findByEntityCode(entityCode, null);
    }

    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(
            String entityCode,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectList(tableName);
        } else {
            dataList = dynamicMapper.selectListWithPermission(
                    tableName,
                    permission.getSqlCondition(),
                            permission.getSqlParameters());
        }

        List<EntityField> runtimeFields = getRuntimeFields(entityCode);
        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(
                        data,
                        entityCode,
                        runtimeFields))
                .toList();
        enrichMultiValues(entityCode, records);
        return records;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(
            String entityCode) {
        return findByEntityCodeSimple(entityCode, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(
            String entityCode,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);

        if (!permission.isHasPermission()) {
            return new ArrayList<>();
        }
        if (!permission.isNeedFilter()) {
            return dynamicMapper.selectList(tableName);
        }
        return dynamicMapper.selectListWithPermission(
                tableName,
                permission.getSqlCondition(),
                            permission.getSqlParameters());
    }

    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPage(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                getDataPermission(entityCode, listKey));
    }

    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageForUser(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            SysUser user) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                dataPermissionEngine.calculatePermission(
                        entityCode,
                        listKey,
                        user));
    }

    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageWithDataScopePlan(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataScopePlan plan) {
        if (plan == null
                || plan.sqlFragment() == null
                || plan.sqlFragment().isBlank()) {
            throw new IllegalArgumentException(
                    "数据权限计划不能为空");
        }
        DataPermissionResult permission = plan.allowed()
                ? createAllowedPermission(plan)
                : DataPermissionResult.denyAll();
        permission.setMatchedRuleNames(plan.matchedPolicies());
        permission.setReleaseVersion(plan.releaseVersion());
        permission.setExplanation(plan.explanation());
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                permission);
    }

    private DataPermissionResult createAllowedPermission(
            DataScopePlan plan) {
        if ("1=1".equals(plan.sqlFragment())) {
            return DataPermissionResult.allowAll();
        }
        return DataPermissionResult.withCondition(
                plan.sqlFragment());
    }

    private PageResult<EntityDataDTO> findPageWithPermission(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataPermissionResult permission) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(
                1,
                Math.min(200, requestedPageSize));
        long offset = (pageNum - 1) * pageSize;
        String tableName =
                dynamicTableService.getTableName(entityCode);
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(
                        definition,
                        condition);
        Map<String, Object> preparedCondition =
                prepared.condition();
        permission.intersect(prepared.sqlCondition());
        if (!permission.isHasPermission()) {
            return new PageResult<>(
                    List.of(),
                    0,
                    pageNum,
                    pageSize);
        }

        PageRows pageRows = loadPageRows(
                tableName,
                preparedCondition,
                permission,
                offset,
                pageSize);
        List<EntityField> runtimeFields =
                getRuntimeFields(entityCode);
        List<EntityDataDTO> records =
                pageRows.rows().stream()
                        .map(data -> recordMapper.toDto(
                                data,
                                entityCode,
                                runtimeFields))
                        .toList();
        enrichMultiValues(entityCode, records);
        return new PageResult<>(
                records,
                pageRows.total(),
                pageNum,
                pageSize);
    }

    private PageRows loadPageRows(
            String tableName,
            Map<String, Object> condition,
            DataPermissionResult permission,
            long offset,
            long pageSize) {
        boolean hasCondition =
                condition != null && !condition.isEmpty();
        if (hasCondition && permission.isNeedFilter()) {
            return new PageRows(
                    dynamicMapper.countByConditionWithPermission(
                            tableName,
                            condition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters()),
                    dynamicMapper.selectPageByConditionWithPermission(
                            tableName,
                            condition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters(),
                            offset,
                            pageSize));
        }
        if (hasCondition) {
            return new PageRows(
                    dynamicMapper.countByCondition(
                            tableName,
                            condition),
                    dynamicMapper.selectPageByCondition(
                            tableName,
                            condition,
                            offset,
                            pageSize));
        }
        if (permission.isNeedFilter()) {
            return new PageRows(
                    dynamicMapper.countWithPermission(
                            tableName,
                            permission.getSqlCondition(),
                            permission.getSqlParameters()),
                    dynamicMapper.selectPageWithPermission(
                            tableName,
                            permission.getSqlCondition(),
                            permission.getSqlParameters(),
                            offset,
                            pageSize));
        }
        return new PageRows(
                dynamicMapper.count(tableName),
                dynamicMapper.selectPage(
                        tableName,
                        offset,
                        pageSize));
    }

    @Transactional(readOnly = true)
    public EntityDataDTO findById(
            String entityCode,
            String id) {
        Map<String, Object> data = dynamicMapper.selectById(
                dynamicTableService.getTableName(entityCode),
                id);
        if (data == null) {
            throw new RuntimeException("数据不存在: " + id);
        }
        return assembleAggregate(data, entityCode);
    }

    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleById(
            String entityCode,
            String id,
            String listKey) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            throw new ForbiddenException(
                    "数据不存在或无权访问");
        }
        Map<String, Object> data =
                permission.isNeedFilter()
                        ? dynamicMapper.selectByIdWithPermission(
                                tableName,
                                id,
                                permission.getSqlCondition(),
                            permission.getSqlParameters())
                        : dynamicMapper.selectById(
                                tableName,
                                id);
        if (data == null) {
            throw new ForbiddenException(
                    "数据不存在或不在当前数据权限范围内");
        }
        return assembleAggregate(data, entityCode);
    }

    /**
     * 历史版本访问校验专用：逻辑删除记录仍按原行级权限判断。
     * 禁止写入、固化或普通详情接口复用此方法。
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleIncludingDeletedById(
            String entityCode,
            String id,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            throw new ForbiddenException("数据不存在或无权访问");
        }
        Map<String, Object> data = permission.isNeedFilter()
                ? dynamicMapper.selectByIdIncludingDeletedWithPermission(
                        tableName,
                        id,
                        permission.getSqlCondition(),
                        permission.getSqlParameters())
                : dynamicMapper.selectByIdIncludingDeleted(tableName, id);
        if (data == null) {
            throw new ForbiddenException("数据不存在或不在当前数据权限范围内");
        }
        return assembleAggregate(data, entityCode);
    }

    @Transactional(readOnly = true)
    public EntityDataDTO findByProcessInstanceId(
            String entityCode,
            String processInstanceId) {
        Map<String, Object> data =
                dynamicMapper.selectByProcessInstanceId(
                        dynamicTableService.getTableName(entityCode),
                        processInstanceId);
        if (data == null) {
            throw new RuntimeException(
                    "数据不存在: " + processInstanceId);
        }
        return assembleAggregate(data, entityCode);
    }

    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleByProcessInstanceId(
            String entityCode,
            String processInstanceId,
            String listKey) {
        Map<String, Object> data =
                dynamicMapper.selectByProcessInstanceId(
                        dynamicTableService.getTableName(entityCode),
                        processInstanceId);
        if (data == null || data.get("id") == null) {
            throw new ForbiddenException(
                    "数据不存在或无权访问");
        }
        return findAccessibleById(
                entityCode,
                String.valueOf(data.get("id")),
                listKey);
    }

    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(
            String entityCode,
            Map<String, Object> condition) {
        return findByCondition(entityCode, null, condition);
    }

    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(
            String entityCode,
            String listKey,
            Map<String, Object> condition) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(
                        requireDefinition(entityCode),
                        condition);
        Map<String, Object> preparedCondition =
                prepared.condition();
        permission.intersect(prepared.sqlCondition());

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectByCondition(
                    tableName,
                    preparedCondition);
        } else {
            dataList =
                    dynamicMapper.selectByConditionWithPermission(
                            tableName,
                            preparedCondition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters());
        }

        List<EntityField> runtimeFields =
                getRuntimeFields(entityCode);
        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(
                        data,
                        entityCode,
                        runtimeFields))
                .toList();
        enrichMultiValues(entityCode, records);
        return records;
    }

    @Transactional(readOnly = true)
    public long count(String entityCode) {
        return count(entityCode, null);
    }

    @Transactional(readOnly = true)
    public long count(
            String entityCode,
            String listKey) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            return 0;
        }
        if (!permission.isNeedFilter()) {
            return dynamicMapper.count(tableName);
        }
        return dynamicMapper.countWithPermission(
                tableName,
                permission.getSqlCondition(),
                            permission.getSqlParameters());
    }

    private EntityDefinition requireDefinition(
            String entityCode) {
        return definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException(
                        "实体不存在: " + entityCode));
    }

    private EntityDataDTO assembleAggregate(
            Map<String, Object> data,
            String entityCode) {
        EntityDataDTO dto = toRuntimeDto(data, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        relationRuntimeService.loadRelationData(dto);
        return dto;
    }

    private void enrichMultiValues(
            String entityCode,
            Collection<EntityDataDTO> records) {
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        if (definition == null
                || definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        multiValueRuntimeService.enrich(
                definition,
                records);
    }

    private SysUser getCurrentSysUser() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return sysUserService.getById(userId);
    }

    private DataPermissionResult getDataPermission(
            String entityCode,
            String listKey) {
        SysUser user = getCurrentSysUser();
        if (user == null) {
            return DataPermissionResult.withCondition(
                    "create_by = ''");
        }
        return dataPermissionEngine.calculatePermission(
                entityCode,
                listKey,
                user);
    }

    private EntityDataDTO toRuntimeDto(
            Map<String, Object> data,
            String entityCode) {
        return recordMapper.toDto(
                data,
                entityCode,
                getRuntimeFields(entityCode));
    }

    private List<EntityField> getRuntimeFields(
            String entityCode) {
        try {
            EntityPublishedSnapshot snapshot =
                    snapshotService.getLatestByEntityCode(
                            entityCode);
            return snapshot.getFields() == null
                    ? List.of()
                    : snapshot.getFields();
        } catch (RuntimeException exception) {
            log.debug(
                    "读取实体发布字段失败，使用兼容字段映射: entityCode={}, reason={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(exception.getMessage()));
            return List.of();
        }
    }

    private record PageRows(
            long total,
            List<Map<String, Object>> rows) {
    }
}
